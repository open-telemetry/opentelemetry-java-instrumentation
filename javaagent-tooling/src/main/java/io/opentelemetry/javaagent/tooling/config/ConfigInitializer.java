/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.javaagent.tooling.config;

import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.javaagent.spi.config.PropertySource;
import io.opentelemetry.javaagent.tooling.AgentInstaller;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;
import java.util.ServiceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ConfigInitializer {
  private static final Logger log = LoggerFactory.getLogger(ConfigInitializer.class);

  private static final String CONFIGURATION_FILE_PROPERTY = "otel.trace.config";
  private static final String CONFIGURATION_FILE_ENV_VAR = "OTEL_TRACE_CONFIG";

  public static void initialize() {
    Config.internalInitializeConfig(
        new ConfigBuilder()
            .readPropertiesFromAllSources(loadSpiConfiguration(), loadConfigurationFile())
            .build());
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

    try (FileReader fileReader = new FileReader(configurationFile)) {
      properties.load(fileReader);
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
