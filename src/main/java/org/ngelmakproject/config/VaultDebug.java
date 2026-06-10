package org.ngelmakproject.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class VaultDebug {

  private static final Logger log = LoggerFactory.getLogger(VaultDebug.class);

  @Autowired
  private Environment env;

  @Value("${jwt-secret-key:NOT_LOADED}")
  private String jwtSecretKey;

  @Value("${spring.datasource.username:NOT_LOADED}")
  private String dbUser;

  @Value("${spring.datasource.password:NOT_LOADED}")
  private String dbPass;

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
