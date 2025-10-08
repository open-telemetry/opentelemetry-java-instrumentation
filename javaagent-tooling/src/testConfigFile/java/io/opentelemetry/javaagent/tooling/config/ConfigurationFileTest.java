/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junitpioneer.jupiter.ClearSystemProperty;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

@ExtendWith(SystemStubsExtension.class)
@ClearSystemProperty(key = "otel.javaagent.configuration-file")
class ConfigurationFileTest {

  @TempDir File tmpDir;

  @SystemStub private EnvironmentVariables environmentVariables;

//  @SystemStub private SystemProperties systemProperties;

  @Test
  void shouldUseEnvVar() throws IOException {
    String path = createFile("config", "property1=val-env");
    environmentVariables.set("OTEL_JAVAAGENT_CONFIGURATION_FILE", path);

    Map<String, String> properties = ConfigurationFile.loadConfigFile();

    assertThat(properties).containsEntry("property1", "val-env");
  }

  @Test
  void shouldUseSystemProperty() throws IOException {
    String path = createFile("config", "property1=val-sys");
    System.setProperty("otel.javaagent.configuration-file", path);

    Map<String, String> properties = ConfigurationFile.loadConfigFile();

    assertThat(properties).containsEntry("property1", "val-sys");
  }

  @Test
  void shouldUseSystemPropertyOverEnvVar() throws IOException {
    String pathEnv = createFile("configEnv", "property1=val-env");
    String path = createFile("config", "property1=val-sys");
    System.setProperty("otel.javaagent.configuration-file", path);
    environmentVariables.set("OTEL_JAVAAGENT_CONFIGURATION_FILE", pathEnv);

    Map<String, String> properties = ConfigurationFile.loadConfigFile();

    assertThat(properties).containsEntry("property1", "val-sys");
  }

  @Test
  void shouldReturnEmptyPropertiesIfFileDoesNotExist() {
    System.setProperty("otel.javaagent.configuration-file", "somePath");

    Map<String, String> properties = ConfigurationFile.loadConfigFile();

    assertThat(properties).isEmpty();
  }

  @Test
  void shouldReturnEmptyPropertiesIfPropertyIsNotSet() {
    Map<String, String> properties = ConfigurationFile.loadConfigFile();

    assertThat(properties).isEmpty();
  }

  private String createFile(String name, String contents) throws IOException {
    File file = new File(tmpDir, name);
    try (Writer writer =
        new OutputStreamWriter(Files.newOutputStream(file.toPath()), StandardCharsets.UTF_8)) {
      writer.write(contents);
    }
    return file.getAbsolutePath();
  }
}
