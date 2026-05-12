/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources.internal;

import static io.opentelemetry.semconv.ServiceAttributes.SERVICE_NAME;
import static io.opentelemetry.semconv.ServiceAttributes.SERVICE_VERSION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import io.opentelemetry.sdk.resources.Resource;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.jar.Manifest;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ManifestResourceExtractorTest {

  @ParameterizedTest(name = "{0}")
  @MethodSource("extractResourceCases")
  void extractResource(
      String name, String expectedName, String expectedVersion, String resourceName)
      throws IOException {
    try (InputStream input = openClasspathResource(resourceName)) {
      Resource resource =
          new ManifestResourceExtractor(
                  new MainJarPathFinder(
                      () -> JarServiceNameResourceExtractorTest.getArgs("app.jar"),
                      prop -> null,
                      JarServiceNameResourceExtractorTest::failPath),
                  p -> {
                    if (input == null) {
                      return Optional.empty();
                    }
                    try {
                      Manifest manifest = new Manifest();
                      manifest.read(input);
                      return Optional.of(manifest);
                    } catch (IOException ignored) {
                      return Optional.empty();
                    }
                  })
              .extract();
      assertThat(resource.getAttribute(SERVICE_NAME)).isEqualTo(expectedName);
      assertThat(resource.getAttribute(SERVICE_VERSION)).isEqualTo(expectedVersion);
    }
  }

  private static Stream<Arguments> extractResourceCases() {
    return Stream.of(
        arguments("name ok", "demo", "0.0.1-SNAPSHOT", "MANIFEST.MF"),
        arguments("name - no resource", null, null, null),
        arguments("name - empty resource", null, null, "empty-MANIFEST.MF"));
  }

  private static InputStream openClasspathResource(String resource) {
    if (resource == null) {
      return null;
    }
    return ManifestResourceExtractorTest.class.getClassLoader().getResourceAsStream(resource);
  }
}
