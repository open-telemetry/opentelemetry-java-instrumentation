/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Rule;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Properties;

class ConfigurationFileTest {

  @TempDir
  File tmpDir;

  @Rule
  public final EnvironmentVariables environmentVariables = new EnvironmentVariables();

  private String originalSystemProperty;
  private String originalEnvironmentVariable;

  @BeforeEach
  void setUp() {
    originalSystemProperty = System.getProperty("otel.javaagent.configuration-file");
    originalEnvironmentVariable = System.getenv("OTEL_JAVAAGENT_CONFIGURATION_FILE");
    System.clearProperty("otel.javaagent.configuration-file");
  }

  @AfterEach
  void tearDown() {
    if (originalSystemProperty != null) {
      System.setProperty("otel.javaagent.configuration-file", originalSystemProperty);
    } else {
      System.clearProperty("otel.javaagent.configuration-file");
    }
  }

  @Test
  void shouldUseEnvVar() throws IOException {
    String path = createFile("config", "property1=val-env");
    environmentVariables.set("OTEL_JAVAAGENT_CONFIGURATION_FILE", path)

    Properties properties = ConfigurationFile.loadConfigFile();

    assertThat(properties.get("property1")).isEqualTo("val-env");
  }

  @Test
  void shouldUseSystemProperty() throws IOException {
    String path = createFile("config", "property1=val-sys");
    System.setProperty("otel.javaagent.configuration-file", path);

    Properties properties = ConfigurationFile.loadConfigFile();

    assertThat(properties.get("property1")).isEqualTo("val-sys");
  }

  @Test
  void shouldUseSystemPropertyOverEnvVar() throws IOException {
    def pathEnv = createFile("configEnv", "property1=val-env")
    String path = createFile("config", "property1=val-sys");
    System.setProperty("otel.javaagent.configuration-file", path);
    environmentVariables.set("OTEL_JAVAAGENT_CONFIGURATION_FILE", pathEnv)

    Properties properties = ConfigurationFile.loadConfigFile();

    assertThat(properties.get("property1")).isEqualTo("val-sys");
  }

  @Test
  void shouldReturnEmptyPropertiesIfFileDoesNotExist() {
    System.setProperty("otel.javaagent.configuration-file", "somePath");

    Properties properties = ConfigurationFile.loadConfigFile();

    assertThat(properties).isEmpty();
  }

  @Test
  void shouldReturnEmptyPropertiesIfPropertyIsNotSet() {
    Properties properties = ConfigurationFile.loadConfigFile();

    assertThat(properties).isEmpty();
  }

  private String createFile(String name, String contents) throws IOException {
    File file = new File(tmpDir, name);
    try (FileWriter writer = new FileWriter(file)) {
      writer.write(contents);
    }
    return file.getAbsolutePath();
  }
}
