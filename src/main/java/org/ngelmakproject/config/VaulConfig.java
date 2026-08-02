package org.ngelmakproject.config;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class VaulConfig {

	private static final Logger log = LoggerFactory.getLogger(VaulConfig.class);

	private final Environment env;
	private final String jwtSecretKey;
	private final String dbUser;
	private final String dbPass;

	public VaulConfig(
			Environment env,
			@Value("${jwt-secret-key:NOT_LOADED}") String jwtSecretKey,
			@Value("${spring.datasource.username:NOT_LOADED}") String dbUser,
			@Value("${spring.datasource.password:NOT_LOADED}") String dbPass) {
		this.env = env;
		this.jwtSecretKey = jwtSecretKey;
		this.dbUser = dbUser;
		this.dbPass = dbPass;
	}

	/**
	 * Builds (and refreshes) the application's {@link DataSource}.
	 * 
	 * @return the data source
	 */
	@Bean
	@RefreshScope
	public DataSource dataSource(DataSourceProperties properties) {
		var db = DataSourceBuilder.create()
				.url(properties.getUrl())
				.username(properties.getUsername())
				.password(properties.getPassword())
				.build();

		// Short + useful, but don't log secrets (password).
		log.info(
				"DataSource rebuilt: urlPresent={}, usernamePresent={}, username={}",
				properties.getUrl() != null,
				properties.getUsername() != null,
				properties.getUsername());

		return db;
	}

	@EventListener(ApplicationReadyEvent.class)
	public void ready() {

		String profile = String.join(",", env.getActiveProfiles());

		String kvEnabled = env.getProperty("spring.cloud.vault.kv.enabled", "false");
		String dbEnabled = env.getProperty("spring.cloud.vault.database.enabled", "false");
		String transitEnabled = env.getProperty("spring.cloud.vault.transit.enabled", "false");

		String kvBackend = env.getProperty("spring.cloud.vault.kv.backend", "kv");
		String kvContext = env.getProperty("spring.cloud.vault.kv.default-context", "");
		String kvAppName = env.getProperty("spring.cloud.vault.kv.application-name", "");

		String kvPath = kvBackend + "/" + kvContext;

		log.info("\n\n" +
				"====================  🔐 VAULT DEBUG INFO  ====================\n" +
				" Active Profile        :  {}\n" +
				"---------------------------------------------------------------\n" +
				" KV Enabled            :  {}\n" +
				" KV Backend            :  {}\n" +
				" KV Default Context    :  {}\n" +
				" KV Application Name   :  '{}'\n" +
				" KV Resolved Path      :  {}\n" +
				"---------------------------------------------------------------\n" +
				" DB Backend Enabled    :  {}\n" +
				" DB Username Loaded    :  {}\n" +
				" DB Password Loaded    :  {}\n" +
				"---------------------------------------------------------------\n" +
				" Transit Enabled       :  {}\n" +
				"---------------------------------------------------------------\n" +
				" JWT Secret Loaded     :  {}\n" +
				"===============================================================\n",
				profile,
				kvEnabled,
				kvBackend,
				kvContext,
				kvAppName,
				kvPath,
				dbEnabled,
				mask(dbUser),
				mask(dbPass),
				transitEnabled,
				mask(jwtSecretKey));
	}

	private String mask(String value) {
		if (value == null || value.equals("NOT_LOADED"))
			return value;
		if (value.length() <= 6)
			return "***";
		return value.substring(0, 3) + "..." + value.substring(value.length() - 3);
	}
}
