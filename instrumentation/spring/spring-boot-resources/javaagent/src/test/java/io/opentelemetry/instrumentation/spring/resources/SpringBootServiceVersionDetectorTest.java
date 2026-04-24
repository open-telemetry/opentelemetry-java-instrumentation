/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.resources;

import static io.opentelemetry.semconv.ServiceAttributes.SERVICE_VERSION;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.resources.Resource;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SpringBootServiceVersionDetectorTest {

  private static final String BUILD_PROPS = "build-info.properties";
  private static final String META_INFO = "META-INF";

  @Mock ConfigProperties config;
  @Mock SystemHelper system;

  @Test
  void givenBuildVersionIsPresentInBuildInfProperties_thenReturnBuildVersion() {
    when(system.openClasspathResource(META_INFO, BUILD_PROPS))
        .thenReturn(openClasspathResource(META_INFO + "/" + BUILD_PROPS));

    SpringBootServiceVersionDetector guesser = new SpringBootServiceVersionDetector(system);
    Resource result = guesser.createResource(config);
    assertThat(result.getAttribute(SERVICE_VERSION)).isEqualTo("0.0.2");
  }

  @Test
  void givenBuildVersionFileNotPresent_thenReturnEmptyResource() {
    when(system.openClasspathResource(META_INFO, BUILD_PROPS)).thenReturn(null);

    SpringBootServiceVersionDetector guesser = new SpringBootServiceVersionDetector(system);
    Resource result = guesser.createResource(config);
    assertThat(result).isEqualTo(Resource.empty());
  }

  @Test
  void givenBuildVersionFileIsPresentButBuildVersionPropertyNotPresent_thenReturnEmptyResource() {
    when(system.openClasspathResource(META_INFO, BUILD_PROPS))
        .thenReturn(openClasspathResource(BUILD_PROPS));

    SpringBootServiceVersionDetector guesser = new SpringBootServiceVersionDetector(system);
    Resource result = guesser.createResource(config);
    assertThat(result).isEqualTo(Resource.empty());
  }

  @Test
  void givenBootInfBuildVersionFileIsPresent_thenReturnBuildVersion() throws IOException {
    Path tempDir = Files.createTempDirectory("spring-boot-resources");
    Path buildInfo = tempDir.resolve("BOOT-INF/classes/META-INF/build-info.properties");
    Files.createDirectories(buildInfo.getParent());
    try (OutputStream out = Files.newOutputStream(buildInfo)) {
      out.write("build.version=1.2.3\n".getBytes(UTF_8));
    }

    ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
    try (URLClassLoader classLoader =
        new URLClassLoader(new URL[] {tempDir.toUri().toURL()}, null)) {
      Thread.currentThread().setContextClassLoader(classLoader);

      SpringBootServiceVersionDetector guesser = new SpringBootServiceVersionDetector();
      Resource result = guesser.createResource(config);
      assertThat(result.getAttribute(SERVICE_VERSION)).isEqualTo("1.2.3");
    } finally {
      Thread.currentThread().setContextClassLoader(originalClassLoader);
      Files.deleteIfExists(buildInfo);
      Files.deleteIfExists(buildInfo.getParent());
      Files.deleteIfExists(buildInfo.getParent().getParent());
      Files.deleteIfExists(buildInfo.getParent().getParent().getParent());
      Files.deleteIfExists(tempDir);
    }
  }

  private InputStream openClasspathResource(String resource) {
    return getClass().getClassLoader().getResourceAsStream(resource);
  }
}
