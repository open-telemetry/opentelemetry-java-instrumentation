/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.resources;

import static io.opentelemetry.semconv.ServiceAttributes.SERVICE_NAME;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.resources.Resource;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SpringBootServiceNameDetectorTest {

  static final String APPLICATION_PROPS = "application.properties";
  static final String APPLICATION_YML = "application.yml";

  static final String BOOTSTRAP_PROPS = "bootstrap.properties";

  static final String BOOTSTRAP_YML = "bootstrap.yml";

  @Mock ConfigProperties config;
  @Mock SystemHelper system;

  @Test
  void findByEnvVar() {
    String expected = "fur-city";
    when(system.getenv("SPRING_APPLICATION_NAME")).thenReturn(expected);

    SpringBootServiceNameDetector guesser = new SpringBootServiceNameDetector(system);

    Resource result = guesser.createResource(config);
    expectServiceName(result, expected);
  }

  @Test
  void classpathApplicationProperties() {
    when(system.openClasspathResource(APPLICATION_PROPS))
        .thenReturn(openClasspathResource(APPLICATION_PROPS));
    SpringBootServiceNameDetector guesser = new SpringBootServiceNameDetector(system);
    Resource result = guesser.createResource(config);
    expectServiceName(result, "dog-store");
  }

  @Test
  void classpathBootstrapProperties() {
    when(system.openClasspathResource(BOOTSTRAP_PROPS))
        .thenReturn(openClasspathResource(BOOTSTRAP_PROPS));
    SpringBootServiceNameDetector guesser = new SpringBootServiceNameDetector(system);
    Resource result = guesser.createResource(config);
    expectServiceName(result, "dog-store-bootstrap");
  }

  @Test
  void propertiesFileInCurrentDir() throws Exception {
    Path propsPath = Paths.get(APPLICATION_PROPS);
    try {
      writeString(propsPath, "spring.application.name=fish-tank\n");
      when(system.openFile(APPLICATION_PROPS)).thenCallRealMethod();
      SpringBootServiceNameDetector guesser = new SpringBootServiceNameDetector(system);
      Resource result = guesser.createResource(config);
      expectServiceName(result, "fish-tank");
    } finally {
      Files.delete(propsPath);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"application.yaml", APPLICATION_YML})
  void classpathApplicationYaml(String fileName) {
    when(system.openClasspathResource(fileName)).thenReturn(openClasspathResource(APPLICATION_YML));
    SpringBootServiceNameDetector guesser = new SpringBootServiceNameDetector(system);
    Resource result = guesser.createResource(config);
    expectServiceName(result, "cat-store");
  }

  @ParameterizedTest
  @ValueSource(strings = {"bootstrap.yaml", BOOTSTRAP_YML})
  void classpathBootstrapYaml(String fileName) {
    when(system.openClasspathResource(fileName)).thenReturn(openClasspathResource(BOOTSTRAP_YML));
    SpringBootServiceNameDetector guesser = new SpringBootServiceNameDetector(system);
    Resource result = guesser.createResource(config);
    expectServiceName(result, "cat-store-bootstrap");
  }

  @ParameterizedTest
  @ValueSource(strings = {"bootstrap.yaml", BOOTSTRAP_YML})
  void classpathBootstrapYamlContainingMultipleYamlDefinitions(String fileName) {
    when(system.openClasspathResource(fileName))
        .thenReturn(ClassLoader.getSystemClassLoader().getResourceAsStream("bootstrap-multi.yml"));
    SpringBootServiceNameDetector guesser = new SpringBootServiceNameDetector(system);
    Resource result = guesser.createResource(config);
    expectServiceName(result, "cat-store-bootstrap");
  }

  @ParameterizedTest
  @ValueSource(strings = {"application.yaml", APPLICATION_YML})
  void classpathApplicationYamlContainingMultipleYamlDefinitions(String fileName) {
    when(system.openClasspathResource(fileName))
        .thenReturn(
            ClassLoader.getSystemClassLoader().getResourceAsStream("application-multi.yml"));
    SpringBootServiceNameDetector guesser = new SpringBootServiceNameDetector(system);
    Resource result = guesser.createResource(config);
    expectServiceName(result, "cat-store");
  }

  @ParameterizedTest
  @ValueSource(strings = {"application.yaml", APPLICATION_YML})
  void yamlFileInCurrentDir(String fileName) throws Exception {
    Path yamlPath = Paths.get(fileName);
    try {
      URL url = getClass().getClassLoader().getResource(APPLICATION_YML);
      String content = readString(Paths.get(url.toURI()));
      writeString(yamlPath, content);
      when(system.openFile(fileName)).thenCallRealMethod();
      SpringBootServiceNameDetector guesser = new SpringBootServiceNameDetector(system);
      Resource result = guesser.createResource(config);
      expectServiceName(result, "cat-store");
    } finally {
      Files.delete(yamlPath);
    }
  }

  @Test
  void getFromCommandlineArgsWithProcessHandle() throws Exception {
    when(system.attemptGetCommandLineArgsViaReflection())
        .thenReturn(
            new String[] {
              "/bin/java",
              "sweet-spring.jar",
              "--spring.application.name=tiger-town",
              "--quiet=never"
            });
    SpringBootServiceNameDetector guesser = new SpringBootServiceNameDetector(system);
    Resource result = guesser.createResource(config);
    expectServiceName(result, "tiger-town");
  }

  @Test
  void getFromCommandlineArgsWithSystemProperty() {
    when(system.getProperty("sun.java.command"))
        .thenReturn("/bin/java sweet-spring.jar --spring.application.name=bullpen --quiet=never");
    SpringBootServiceNameDetector guesser = new SpringBootServiceNameDetector(system);
    Resource result = guesser.createResource(config);
    expectServiceName(result, "bullpen");
  }

  @Test
  void shouldApply() {
    SpringBootServiceNameDetector guesser = new SpringBootServiceNameDetector(system);
    assertThat(guesser.shouldApply(config, Resource.getDefault())).isTrue();
  }

  @Test
  void shouldNotApplyWhenResourceHasServiceName() {
    SpringBootServiceNameDetector guesser = new SpringBootServiceNameDetector(system);
    Resource resource =
        Resource.getDefault().merge(Resource.create(Attributes.of(SERVICE_NAME, "test-service")));
    assertThat(guesser.shouldApply(config, resource)).isFalse();
  }

  @Test
  void shouldNotApplyIfConfigHasServiceName() {
    SpringBootServiceNameDetector guesser = new SpringBootServiceNameDetector(system);
    when(config.getString("otel.service.name")).thenReturn("test-service");
    assertThat(guesser.shouldApply(config, Resource.getDefault())).isFalse();
  }

  @Test
  void shouldNotApplyIfConfigHasServiceNameResourceAttribute() {
    SpringBootServiceNameDetector guesser = new SpringBootServiceNameDetector(system);
    when(config.getMap("otel.resource.attributes"))
        .thenReturn(singletonMap(SERVICE_NAME.getKey(), "test-service"));
    assertThat(guesser.shouldApply(config, Resource.getDefault())).isFalse();
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
    byte[] allBytes = Files.readAllBytes(path);
    return new String(allBytes, UTF_8);
  }

  private InputStream openClasspathResource(String resource) {
    return getClass().getClassLoader().getResourceAsStream(resource);
  }
}
