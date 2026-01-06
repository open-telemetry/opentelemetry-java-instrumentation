/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.config;

import static io.opentelemetry.api.incubator.config.DeclarativeConfigProperties.empty;
import static java.util.logging.Level.WARNING;

import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.internal.ConfigPropertiesUtil;
import io.opentelemetry.sdk.extension.incubator.fileconfig.DeclarativeConfiguration;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Logger;
import javax.annotation.Nullable;

final class DeclarativeConfigurationFile {

  static final String DECLARATIVE_CONFIG_FILE_PROPERTY = "otel.experimental.config.file";

  private static DeclarativeConfigProperties configProperties;

  // this class is used early, and must not use logging in most of its methods
  // in case any file loading/parsing error occurs, we save the error message and log it later, when
  // the logging subsystem is initialized
  @Nullable private static String fileLoadErrorMessage;

  static boolean isConfigured() {
    String configFilePath = ConfigPropertiesUtil.getString(DECLARATIVE_CONFIG_FILE_PROPERTY);
    return configFilePath != null && !configFilePath.isEmpty();
  }

  static DeclarativeConfigProperties getProperties() {
    if (configProperties == null) {
      configProperties = loadConfigFile();
    }
    return configProperties;
  }

  // visible for tests
  static void resetForTest() {
    configProperties = null;
    fileLoadErrorMessage = null;
  }

  // visible for tests
  static DeclarativeConfigProperties loadConfigFile() {
    String configFilePath = ConfigPropertiesUtil.getString(DECLARATIVE_CONFIG_FILE_PROPERTY);
    if (configFilePath == null || configFilePath.isEmpty()) {
      return empty();
    }

    // Normalizing tilde (~) paths for unix systems
    configFilePath = configFilePath.replaceFirst("^~", System.getProperty("user.home"));

    Path file = Paths.get(configFilePath);
    if (!Files.exists(file)) {
      fileLoadErrorMessage = "Declarative configuration file \"" + configFilePath + "\" not found.";
      return empty();
    }

    try (InputStream is = Files.newInputStream(file)) {
      return DeclarativeConfiguration.toConfigProperties(is);
    } catch (IOException e) {
      fileLoadErrorMessage =
          "Declarative configuration file \""
              + configFilePath
              + "\" cannot be accessed or correctly parsed.";
      return empty();
    }
  }

  static void logErrorIfAny() {
    if (fileLoadErrorMessage != null) {
      Logger.getLogger(DeclarativeConfigurationFile.class.getName())
          .log(WARNING, fileLoadErrorMessage);
    }
  }

  private DeclarativeConfigurationFile() {}
}
