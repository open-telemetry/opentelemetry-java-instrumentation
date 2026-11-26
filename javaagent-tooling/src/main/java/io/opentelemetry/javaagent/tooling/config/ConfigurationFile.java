/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.config;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptyMap;
import static java.util.logging.Level.SEVERE;
import static java.util.stream.Collectors.toMap;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;
import javax.annotation.Nullable;

final class ConfigurationFile {

  // same logger name these errors were reported under before
  private static final Logger logger =
      Logger.getLogger(ConfigurationPropertiesSupplier.class.getName());

  @Nullable private static Map<String, String> configFileContents;

  static Map<String, String> getProperties() {
    if (configFileContents == null) {
      configFileContents = loadConfigFile();
    }
    return configFileContents;
  }

  // visible for tests
  static void resetForTest() {
    configFileContents = null;
  }

  // visible for tests
  static Map<String, String> loadConfigFile() {
    // Reading from system property first and from env after
    String configurationFilePath = System.getProperty("otel.javaagent.configuration-file");
    if (configurationFilePath == null) {
      configurationFilePath = System.getenv("OTEL_JAVAAGENT_CONFIGURATION_FILE");
    }
    if (configurationFilePath == null) {
      return emptyMap();
    }

    // Normalizing tilde (~) paths for unix systems
    configurationFilePath =
        configurationFilePath.replaceFirst("^~", System.getProperty("user.home"));

    // Configuration properties file is optional
    File configurationFile = new File(configurationFilePath);
    if (!configurationFile.exists()) {
      logger.log(SEVERE, "Configuration file \"" + configurationFilePath + "\" not found.");
      return emptyMap();
    }

    Properties properties = new Properties();
    try (InputStreamReader reader =
        new InputStreamReader(new FileInputStream(configurationFile), UTF_8)) {
      properties.load(reader);
    } catch (FileNotFoundException fnf) {
      logger.log(SEVERE, "Configuration file \"" + configurationFilePath + "\" not found.");
    } catch (IOException ioe) {
      logger.log(
          SEVERE,
          "Configuration file \""
              + configurationFilePath
              + "\" cannot be accessed or correctly parsed.");
    }

    return properties.entrySet().stream()
        .collect(toMap(e -> e.getKey().toString(), e -> e.getValue().toString()));
  }

  private ConfigurationFile() {}
}
