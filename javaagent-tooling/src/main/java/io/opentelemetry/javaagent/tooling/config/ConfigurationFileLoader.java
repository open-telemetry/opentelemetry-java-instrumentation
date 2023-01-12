/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.config;

import static java.util.Collections.emptyMap;
import static java.util.logging.Level.SEVERE;

import com.google.auto.service.AutoService;
import io.opentelemetry.instrumentation.api.internal.ConfigPropertiesUtil;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizer;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@AutoService(AutoConfigurationCustomizerProvider.class)
public final class ConfigurationFileLoader implements AutoConfigurationCustomizerProvider {

  private static final Logger logger = Logger.getLogger(ConfigurationFileLoader.class.getName());

  static final String CONFIGURATION_FILE_PROPERTY = "otel.javaagent.configuration-file";

  private static Map<String, String> configFileContents;

  @Override
  public void customize(AutoConfigurationCustomizer autoConfiguration) {
    autoConfiguration.addPropertiesSupplier(ConfigurationFileLoader::getConfigFileContents);
  }

  static Map<String, String> getConfigFileContents() {
    if (configFileContents == null) {
      configFileContents = loadConfigFile();
    }
    return configFileContents;
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
      logger.log(SEVERE, "Configuration file \"{0}\" not found.", configurationFilePath);
      return emptyMap();
    }

    Properties properties = new Properties();
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

    return properties.entrySet().stream()
        .collect(Collectors.toMap(e -> e.getKey().toString(), e -> e.getValue().toString()));
  }

  @Override
  public int order() {
    // make sure it runs after all the user-provided customizers
    return Integer.MAX_VALUE;
  }
}
