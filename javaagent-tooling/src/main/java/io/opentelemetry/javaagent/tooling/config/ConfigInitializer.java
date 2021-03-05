/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.config;

import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.instrumentation.api.config.ConfigBuilder;
import io.opentelemetry.javaagent.spi.config.PropertySource;
import io.opentelemetry.javaagent.tooling.AgentInstaller;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.ServiceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ConfigInitializer {
  private static final Logger log = LoggerFactory.getLogger(ConfigInitializer.class);

  private static final String CONFIGURATION_FILE_PROPERTY = "otel.javaagent.configuration-file";
  private static final String CONFIGURATION_FILE_ENV_VAR = "OTEL_JAVAAGENT_CONFIGURATION_FILE";

  public static void initialize() {
    Config.internalInitializeConfig(create(loadSpiConfiguration(), loadConfigurationFile()));
  }

  // visible for testing
  static Config create(Properties spiConfiguration, Properties configurationFile) {
    return new ConfigBuilder()
        .readProperties(spiConfiguration)
        .readProperties(configurationFile)
        .readEnvironmentVariables()
        .readSystemProperties()
        .build();
  }

  /** Retrieves all default configuration overloads using SPI and initializes Config. */
  private static Properties loadSpiConfiguration() {
    Properties propertiesFromSpi = new Properties();
    for (PropertySource propertySource :
        ServiceLoader.load(PropertySource.class, AgentInstaller.class.getClassLoader())) {
      propertiesFromSpi.putAll(propertySource.getProperties());
    }
    return propertiesFromSpi;
  }

  /**
   * Loads the optional configuration properties file into the global {@link Properties} object.
   *
   * @return The {@link Properties} object. the returned instance might be empty of file does not
   *     exist or if it is in a wrong format.
   */
  private static Properties loadConfigurationFile() {
    Properties properties = new Properties();

    // Reading from system property first and from env after
    String configurationFilePath = System.getProperty(CONFIGURATION_FILE_PROPERTY);
    if (configurationFilePath == null) {
      configurationFilePath = System.getenv(CONFIGURATION_FILE_ENV_VAR);
    }
    if (configurationFilePath == null) {
      return properties;
    }

    // Normalizing tilde (~) paths for unix systems
    configurationFilePath =
        configurationFilePath.replaceFirst("^~", System.getProperty("user.home"));

    // Configuration properties file is optional
    File configurationFile = new File(configurationFilePath);
    if (!configurationFile.exists()) {
      log.error("Configuration file '{}' not found.", configurationFilePath);
      return properties;
    }

    try (InputStreamReader reader =
        new InputStreamReader(new FileInputStream(configurationFile), StandardCharsets.UTF_8)) {
      properties.load(reader);
    } catch (FileNotFoundException fnf) {
      log.error("Configuration file '{}' not found.", configurationFilePath);
    } catch (IOException ioe) {
      log.error(
          "Configuration file '{}' cannot be accessed or correctly parsed.", configurationFilePath);
    }

    return properties;
  }

  private ConfigInitializer() {}
}
