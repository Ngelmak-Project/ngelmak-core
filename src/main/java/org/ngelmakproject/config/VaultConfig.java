package org.ngelmakproject.config;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.vault.core.lease.SecretLeaseContainer;
import org.springframework.vault.core.lease.domain.RequestedSecret;
import org.springframework.vault.core.lease.event.AfterSecretLeaseRenewedEvent;
import org.springframework.vault.core.lease.event.SecretLeaseCreatedEvent;
import org.springframework.vault.core.lease.event.SecretLeaseErrorEvent;
import org.springframework.vault.core.lease.event.SecretLeaseExpiredEvent;

import com.zaxxer.hikari.HikariDataSource;

/**
 * Central Vault integration and dynamic credential management for the
 * application.
 * <p>
 * This component provides:
 * <ul>
 * <li>Dynamic construction of the application's {@link DataSource} using
 * Vault‑managed secrets.</li>
 * <li>Automatic rotation of Vault dynamic database credentials when lease
 * renewal is no longer possible.</li>
 * <li>Startup diagnostics showing Vault configuration and resolved secret
 * paths.</li>
 * </ul>
 * <p>
 * The rotation listener ensures that when Vault revokes or refuses to renew a
 * database lease,
 * new credentials are requested and applied to the active
 * {@link HikariDataSource} without
 * requiring an application restart.
 */
@Component
public class VaultConfig {

	private static final Logger log = LoggerFactory.getLogger(VaultConfig.class);
	private static final long ROTATION_THRESHOLD_SECONDS = 1800; // 30 minutes

	private final Environment env;
	private final String jwtSecretKey;
	private final String dbUser;
	private final String dbPass;
	private final String vaultDbRole;

	/**
	 * Constructs the Vault configuration component and loads required secrets from
	 * the environment.
	 *
	 * @param env          Spring environment for resolving active profiles and
	 *                     Vault properties
	 * @param jwtSecretKey JWT signing key loaded from Vault or configuration
	 * @param dbUser       Database username resolved from Vault dynamic credentials
	 * @param dbPass       Database password resolved from Vault dynamic credentials
	 * @param vaultDbRole  Vault database role used to generate dynamic credentials
	 */
	public VaultConfig(
			Environment env,
			@Value("${jwt-secret-key:NOT_LOADED}") String jwtSecretKey,
			@Value("${spring.datasource.username:NOT_LOADED}") String dbUser,
			@Value("${spring.datasource.password:NOT_LOADED}") String dbPass,
			@Value("${spring.cloud.vault.database.role}") String vaultDbRole) {

		this.env = env;
		this.jwtSecretKey = jwtSecretKey;
		this.dbUser = dbUser;
		this.dbPass = dbPass;
		this.vaultDbRole = vaultDbRole;
	}

	/**
	 * Builds the application's {@link DataSource} using Vault‑managed credentials.
	 * <p>
	 * Annotated with {@link RefreshScope} so that when Spring Cloud Vault refreshes
	 * secrets,
	 * the DataSource is rebuilt automatically with the new credentials.
	 *
	 * @param properties resolved database properties from Spring Boot
	 * @return a fully configured {@link DataSource}
	 */
	@Bean
	@RefreshScope
	public DataSource dataSource(DataSourceProperties properties) {
		var db = DataSourceBuilder.create()
				.url(properties.getUrl())
				.username(properties.getUsername())
				.password(properties.getPassword())
				.build();

		log.info(
				"DataSource rebuilt: urlPresent={}, usernamePresent={}, username={}",
				properties.getUrl() != null,
				properties.getUsername() != null,
				properties.getUsername());

		return db;
	}

	/**
	 * Registers a listener that reacts to Vault lease lifecycle events and performs
	 * proactive rotation of dynamic database credentials.
	 * <p>
	 * The listener monitors lease renewal events
	 * ({@link AfterSecretLeaseRenewedEvent}) and checks the remaining TTL
	 * (time-to-live).
	 * When the TTL drops below a configured threshold, it triggers credential
	 * rotation before the lease expires, ensuring seamless credential updates
	 * without connection interruption.
	 * <p>
	 * When rotated credentials are issued in ROTATE mode, they are immediately
	 * applied to the active {@link HikariDataSource}, and existing connections are
	 * soft-evicted to force use of the new credentials.
	 *
	 * @param leaseContainer Vault lease container managing secret lifecycles
	 * @param context        Spring application context used to retrieve the active
	 *                       DataSource bean
	 * @return a marker bean enabling listener registration
	 */
	@Bean
	public Object vaultDbRotationListener(SecretLeaseContainer leaseContainer, ApplicationContext context) {
		final String leasePath = "database/creds/" + vaultDbRole;

		log.debug("Vault DB rotation listener initialized for path '{}'.", leasePath);

		leaseContainer.addLeaseListener(event -> {
			if (!event.getSource().getPath().equals(leasePath)) {
				return;
			}
			log.debug("Vault lease event received: type={}, mode={}",
					event.getClass().getSimpleName(),
					event.getSource().getMode());

			switch (event) {
				// Check TTL on each renewal and rotate if needed
				case AfterSecretLeaseRenewedEvent e -> {
					long ttlSeconds = e.getLease().getLeaseDuration().getSeconds();
					log.debug("Lease renewed, TTL: {} seconds", ttlSeconds);

					if (ttlSeconds < ROTATION_THRESHOLD_SECONDS) {
						log.warn("TTL {} seconds is below threshold of {} seconds, initiating credential rotation",
								ttlSeconds, ROTATION_THRESHOLD_SECONDS);
						leaseContainer.requestRotatingSecret(leasePath);
					}
				}
				// Apply rotated credentials to the datasource
				case SecretLeaseCreatedEvent e when e.getSource().getMode() == RequestedSecret.Mode.ROTATE -> {
					String username = (String) e.getSecrets().get("username");
					String password = (String) e.getSecrets().get("password");

					if (username == null || password == null) {
						log.error("Rotated credentials incomplete, cannot apply to datasource");
						return;
					}

					log.debug("Applying rotated database credentials for user: {}", username);

					HikariDataSource ds = context.getBean(HikariDataSource.class);
					ds.getHikariPoolMXBean().softEvictConnections();
					ds.getHikariConfigMXBean().setUsername(username);
					ds.getHikariConfigMXBean().setPassword(password);

					log.debug("HikariDataSource updated, existing connections evicted");
				}
				// Handle lease expiration fallback
				case SecretLeaseExpiredEvent e -> {
					log.warn("Lease expired, requesting credential rotation");
					leaseContainer.requestRotatingSecret(leasePath);
				}
				// Handle errors
				case SecretLeaseErrorEvent e -> {
					log.error("Vault error: {}", e.getException().getMessage(), e.getException());
				}
				default -> {
				} // No action for other event types
			}
		});

		return new Object();
	}

	/**
	 * Logs Vault‑related configuration details once the application is fully
	 * initialized.
	 * <p>
	 * This provides a clear diagnostic snapshot of Vault integration, active
	 * profiles,
	 * enabled backends, and resolved secret paths.
	 */
	public void ready() {
		String profile = String.join(",", env.getActiveProfiles());

		String kvEnabled = env.getProperty("spring.cloud.vault.kv.enabled", "false");
		String dbEnabled = env.getProperty("spring.cloud.vault.database.enabled", "false");
		String transitEnabled = env.getProperty("spring.cloud.vault.transit.enabled", "false");

		String kvBackend = env.getProperty("spring.cloud.vault.kv.backend", "kv");
		String kvSeparator = env.getProperty("spring.cloud.vault.kv.profile-separator", "/");
		String kvAppName = env.getProperty("spring.cloud.vault.kv.application-name", "");

		String kvPath = kvBackend;
		if (!profile.isEmpty())
			kvPath += kvSeparator + profile;

		log.info("\n" +
				"────────────────────────────────────────────────────────────────\n" +
				" Active Profile        :  {}\n" +
				"────────────────────────────────────────────────────────────────\n" +
				" KV Enabled            :  {}\n" +
				" KV Backend            :  {}\n" +
				" KV Application Name   :  {}\n" +
				" KV Resolved Path      :  {}\n" +
				"────────────────────────────────────────────────────────────────\n" +
				" DB Backend Enabled    :  {}\n" +
				" DB Username Loaded    :  {}\n" +
				" DB Password Loaded    :  {}\n" +
				"────────────────────────────────────────────────────────────────\n" +
				" JWT Secret Loaded     :  {}\n" +
				"────────────────────────────────────────────────────────────────\n" +
				" Transit Enabled       :  {}\n" +
				"────────────────────────────────────────────────────────────────\n",
				profile,
				kvEnabled,
				kvBackend,
				kvAppName,
				kvPath,
				dbEnabled,
				mask(dbUser),
				mask(dbPass),
				mask(jwtSecretKey),
				transitEnabled);
	}

	/**
	 * Masks sensitive values for safe logging.
	 *
	 * @param value the raw secret value
	 * @return a masked representation suitable for logs
	 */
	private String mask(String value) {
		if (value == null || value.equals("NOT_LOADED"))
			return value;
		if (value.length() <= 6)
			return "***";
		return value.substring(0, 3) + "..." + value.substring(value.length() - 3);
	}
}
