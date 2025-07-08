/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.config;

import static io.opentelemetry.javaagent.tooling.config.ErrorBuffer.addErrorMessage;
import static java.util.Collections.emptyMap;

import io.opentelemetry.instrumentation.api.internal.ConfigPropertiesUtil;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

final class ConfigurationFile {

  static final String CONFIGURATION_FILE_PROPERTY = "otel.javaagent.configuration-file";

  private static Map<String, String> configFileContents;

  // this class is used early, and must not use logging in most of its methods
  // in case any file loading/parsing error occurs, we save the error message and log it later, when
  // the logging subsystem is initialized
  @Nullable private static String fileLoadErrorMessage;

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
    String configurationFilePath = ConfigPropertiesUtil.getString(CONFIGURATION_FILE_PROPERTY);
    if (configurationFilePath == null) {
      return emptyMap();
    }

    // Normalizing tilde (~) paths for unix systems
    configurationFilePath =
        configurationFilePath.replaceFirst("^~", System.getProperty("user.home"));

    // Configuration properties file is optional
    File configurationFile = new File(configurationFilePath);
    if (!configurationFile.exists()) {
      addErrorMessage("Configuration file \"" + configurationFilePath + "\" not found.");
      return emptyMap();
    }

    Properties properties = new Properties();
    try (InputStreamReader reader =
        new InputStreamReader(new FileInputStream(configurationFile), StandardCharsets.UTF_8)) {
      properties.load(reader);
    } catch (FileNotFoundException fnf) {
      addErrorMessage("Configuration file \"" + configurationFilePath + "\" not found.");
    } catch (IOException ioe) {
      addErrorMessage(
          "Configuration file \""
              + configurationFilePath
              + "\" cannot be accessed or correctly parsed.");
    }

    return properties.entrySet().stream()
        .collect(Collectors.toMap(e -> e.getKey().toString(), e -> e.getValue().toString()));
  }

  private ConfigurationFile() {}
}
