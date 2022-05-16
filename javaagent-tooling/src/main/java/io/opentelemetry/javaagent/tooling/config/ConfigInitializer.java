/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.config;

import static io.opentelemetry.javaagent.tooling.SafeServiceLoader.loadOrdered;
import static java.util.logging.Level.SEVERE;

import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.javaagent.extension.config.ConfigCustomizer;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

public final class ConfigInitializer {
  private static final Logger logger = Logger.getLogger(ConfigInitializer.class.getName());

  // visible for testing
  static final String CONFIGURATION_FILE_PROPERTY = "otel.javaagent.configuration-file";
  static final String CONFIGURATION_FILE_ENV_VAR = "OTEL_JAVAAGENT_CONFIGURATION_FILE";

  public static void initialize() {
    List<ConfigCustomizer> customizers = loadOrdered(ConfigCustomizer.class);
    Config config = create(loadSpiConfiguration(customizers), loadConfigurationFile());
    for (ConfigCustomizer customizer : customizers) {
      config = customizer.customize(config);
    }
    Config.internalInitializeConfig(config);
  }

  // visible for testing
  static Config create(Properties spiConfiguration, Properties configurationFile) {
    return Config.builder()
        .addProperties(spiConfiguration)
        .addProperties(configurationFile)
        .addEnvironmentVariables()
        .addSystemProperties()
        .build();
  }

  /** Retrieves all default configuration overloads using SPI and initializes Config. */
  @SuppressWarnings("deprecation") // loads the old config SPI
  private static Properties loadSpiConfiguration(List<ConfigCustomizer> customizers) {
    Properties propertiesFromSpi = new Properties();
    for (io.opentelemetry.javaagent.extension.config.ConfigPropertySource propertySource :
        loadOrdered(io.opentelemetry.javaagent.extension.config.ConfigPropertySource.class)) {
      propertiesFromSpi.putAll(propertySource.getProperties());
    }
    for (ConfigCustomizer customizer : customizers) {
      propertiesFromSpi.putAll(customizer.defaultProperties());
    }
    return propertiesFromSpi;
  }

  /**
   * Loads the optional configuration properties file into the global {@link Properties} object.
   *
   * @return The {@link Properties} object. the returned instance might be empty of file does not
   *     exist or if it is in a wrong format.
   */
  // visible for testing
  static Properties loadConfigurationFile() {
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
      logger.log(SEVERE, "Configuration file \"{0}\" not found.", configurationFilePath);
      return properties;
    }

    try (InputStreamReader reader =
        new InputStreamReader(new FileInputStream(configurationFile), StandardCharsets.UTF_8)) {
      properties.load(reader);
    } catch (FileNotFoundException fnf) {
      logger.log(SEVERE, "Configuration file \"{0}\" not found.", configurationFilePath);
    } catch (IOException ioe) {
      logger.log(
          SEVERE,
          "Configuration file \"{0}\" cannot be accessed or correctly parsed.",
          configurationFilePath);
    }

    return properties;
  }

  private ConfigInitializer() {}
}
