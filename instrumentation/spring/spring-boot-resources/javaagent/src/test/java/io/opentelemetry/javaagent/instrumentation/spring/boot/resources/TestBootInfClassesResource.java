/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.boot.resources;

import static io.opentelemetry.semconv.ServiceAttributes.SERVICE_NAME;
import static io.opentelemetry.semconv.ServiceAttributes.SERVICE_VERSION;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.resources.Resource;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLClassLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TestBootInfClassesResource {
  @Mock ConfigProperties config;
  private ClassLoader originalClassLoader;
  private URLClassLoader fixtureClassLoader;

  @BeforeEach
  void setUp() throws IOException {
    URL resource =
        requireNonNull(getClass().getResource("/BOOT-INF/classes/application.properties"));
    URL jar = ((JarURLConnection) resource.openConnection()).getJarFileURL();
    fixtureClassLoader = new URLClassLoader(new URL[] {jar}, null);
    originalClassLoader = Thread.currentThread().getContextClassLoader();
    Thread.currentThread().setContextClassLoader(fixtureClassLoader);
  }

  @AfterEach
  void tearDown() throws IOException {
    Thread.currentThread().setContextClassLoader(originalClassLoader);
    fixtureClassLoader.close();
  }

  @Test
  void testServiceName() {
    // verify that the test app, that is added as a dependency to this project, has the expected
    // layout
    assertThat(fixtureClassLoader.getResource("application.properties")).isNull();
    assertThat(fixtureClassLoader.getResource("BOOT-INF/classes/application.properties"))
        .isNotNull();

    SpringBootServiceNameDetector guesser = new SpringBootServiceNameDetector();
    Resource result = guesser.createResource(config);
    assertThat(result.getAttribute(SERVICE_NAME)).isEqualTo("otel-spring-test-app");
  }

  @Test
  void testServiceVersion() {
    // verify that the test app, that is added as a dependency to this project, has the expected
    // layout: build-info.properties lives at the jar root under META-INF/, not under
    // BOOT-INF/classes/
    assertThat(fixtureClassLoader.getResource("META-INF/build-info.properties")).isNotNull();
    assertThat(fixtureClassLoader.getResource("BOOT-INF/classes/META-INF/build-info.properties"))
        .isNull();

    SpringBootServiceVersionDetector guesser = new SpringBootServiceVersionDetector();
    Resource result = guesser.createResource(config);
    assertThat(result.getAttribute(SERVICE_VERSION)).isEqualTo("1.2.3");
  }
}
