/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.resources;

import static io.opentelemetry.semconv.resource.attributes.ResourceAttributes.SERVICE_NAME;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.resources.Resource;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SpringBootServiceNameGuesserTest {

  static final String PROPS = "application.properties";
  static final String APPLICATION_YML = "application.yml";
  @Mock ConfigProperties config;
  @Mock SpringBootServiceNameGuesser.SystemHelper system;

  @Test
  void findByEnvVar() {
    String expected = "fur-city";
    when(system.getenv("SPRING_APPLICATION_NAME")).thenReturn(expected);

    SpringBootServiceNameGuesser guesser = new SpringBootServiceNameGuesser(system);

    Resource result = guesser.createResource(config);
    expectServiceName(result, expected);
  }

  @Test
  void classpathApplicationProperties() {
    when(system.openClasspathResource(PROPS)).thenCallRealMethod();
    SpringBootServiceNameGuesser guesser = new SpringBootServiceNameGuesser(system);
    Resource result = guesser.createResource(config);
    expectServiceName(result, "dog-store");
  }

  @Test
  void propertiesFileInCurrentDir() throws Exception {
    Path propsPath = Paths.get(PROPS);
    try {
      writeString(propsPath, "spring.application.name=fish-tank\n");
      when(system.openFile(PROPS)).thenCallRealMethod();
      SpringBootServiceNameGuesser guesser = new SpringBootServiceNameGuesser(system);
      Resource result = guesser.createResource(config);
      expectServiceName(result, "fish-tank");
    } finally {
      Files.delete(propsPath);
    }
  }

  @Test
  void classpathApplicationYaml() {
    when(system.openClasspathResource(APPLICATION_YML)).thenCallRealMethod();
    SpringBootServiceNameGuesser guesser = new SpringBootServiceNameGuesser(system);
    Resource result = guesser.createResource(config);
    expectServiceName(result, "cat-store");
  }

  @Test
  void yamlFileInCurrentDir() throws Exception {
    Path yamlPath = Paths.get(APPLICATION_YML);
    try {
      URL url = getClass().getClassLoader().getResource(APPLICATION_YML);
      String content = readString(Paths.get(url.toURI()));
      writeString(yamlPath, content);
      when(system.openFile(PROPS)).thenThrow(new FileNotFoundException());
      when(system.openFile(APPLICATION_YML)).thenCallRealMethod();
      SpringBootServiceNameGuesser guesser = new SpringBootServiceNameGuesser(system);
      Resource result = guesser.createResource(config);
      expectServiceName(result, "cat-store");
    } finally {
      Files.delete(yamlPath);
    }
  }

  @Test
  void getFromCommandlineArgsWithProcessHandle() throws Exception {
    when(system.attemptGetCommandLineViaReflection())
        .thenReturn(
            "/bin/java sweet-spring.jar --spring.application.name=tiger-town --quiet=never");
    SpringBootServiceNameGuesser guesser = new SpringBootServiceNameGuesser(system);
    Resource result = guesser.createResource(config);
    expectServiceName(result, "tiger-town");
  }

  @Test
  void getFromCommandlineArgsWithSystemProperty() throws Exception {
    when(system.getProperty("sun.java.command"))
        .thenReturn("/bin/java sweet-spring.jar --spring.application.name=bullpen --quiet=never");
    SpringBootServiceNameGuesser guesser = new SpringBootServiceNameGuesser(system);
    Resource result = guesser.createResource(config);
    expectServiceName(result, "bullpen");
  }

  private static void expectServiceName(Resource result, String expected) {
    assertThat(result.getAttribute(SERVICE_NAME)).isEqualTo(expected);
  }

  private static void writeString(Path path, String value) throws Exception {
    try (OutputStream out = Files.newOutputStream(path)) {
      out.write(value.getBytes(UTF_8));
    }
  }

  private static String readString(Path path) throws Exception {
    StringBuilder buff = new StringBuilder();
    try (InputStream in = Files.newInputStream(path)) {
      BufferedReader reader = new BufferedReader(new InputStreamReader(in, UTF_8));
      char[] block = new char[1024];
      int rc;
      while ((rc = reader.read(block)) > 0) {
        buff.append(block, 0, rc);
      }
      return buff.toString();
    }
  }
}
