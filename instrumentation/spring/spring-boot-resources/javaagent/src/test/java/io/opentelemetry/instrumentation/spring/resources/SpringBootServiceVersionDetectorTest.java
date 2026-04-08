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
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SpringBootServiceVersionDetectorTest {

  static final String BUILD_PROPS = "build-info.properties";
  static final String META_INFO = "META-INF";

  @Mock ConfigProperties config;
  @Mock SystemHelper system;
  @TempDir Path tempDir;

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
  void givenBootInfBuildInfo_thenReturnBuildVersion() throws Exception {
    Path bootInfClasses = Files.createDirectories(tempDir.resolve("BOOT-INF/classes/META-INF"));
    Files.write(bootInfClasses.resolve(BUILD_PROPS), "build.version=0.0.3\n".getBytes(UTF_8));

    ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
    URL classpathRoot = tempDir.toUri().toURL();
    try (URLClassLoader classLoader = new URLClassLoader(new URL[] {classpathRoot}, null)) {
      Thread.currentThread().setContextClassLoader(classLoader);

      SpringBootServiceVersionDetector guesser =
          new SpringBootServiceVersionDetector(new SystemHelper());
      Resource result = guesser.createResource(config);

      assertThat(result.getAttribute(SERVICE_VERSION)).isEqualTo("0.0.3");
    } finally {
      Thread.currentThread().setContextClassLoader(originalClassLoader);
    }
  }

  private InputStream openClasspathResource(String resource) {
    return getClass().getClassLoader().getResourceAsStream(resource);
  }
}
