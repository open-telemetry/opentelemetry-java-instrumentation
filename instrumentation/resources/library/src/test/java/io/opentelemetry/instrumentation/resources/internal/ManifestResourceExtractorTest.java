/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources.internal;

import static io.opentelemetry.semconv.ServiceAttributes.SERVICE_NAME;
import static io.opentelemetry.semconv.ServiceAttributes.SERVICE_VERSION;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.sdk.resources.Resource;
import java.io.InputStream;
import java.util.Collection;
import java.util.Optional;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

class ManifestResourceExtractorTest {

  private static class TestCase {
    private final String name;
    private final String expectedName;
    private final String expectedVersion;
    private final InputStream input;

    TestCase(String name, String expectedName, String expectedVersion, InputStream input) {
      this.name = name;
      this.expectedName = expectedName;
      this.expectedVersion = expectedVersion;
      this.input = input;
    }
  }

  @TestFactory
  Collection<DynamicTest> extractResource() {
    return Stream.of(
            new TestCase("name ok", "demo", "0.0.1-SNAPSHOT", openClasspathResource("MANIFEST.MF")),
            new TestCase("name - no resource", null, null, null),
            new TestCase(
                "name - empty resource", null, null, openClasspathResource("empty-MANIFEST.MF")))
        .map(
            t ->
                DynamicTest.dynamicTest(
                    t.name,
                    () -> {
                      Resource resource =
                          new ManifestResourceExtractor(
                                  new MainJarPathFinder(
                                      () -> JarServiceNameResourceExtractorTest.getArgs("app.jar"),
                                      prop -> null,
                                      JarServiceNameResourceExtractorTest::failPath),
                                  p -> {
                                    try {
                                      Manifest manifest = new Manifest();
                                      manifest.read(t.input);
                                      return Optional.of(manifest);
                                    } catch (Exception e) {
                                      return Optional.empty();
                                    }
                                  })
                              .extract();
                      assertThat(resource.getAttribute(SERVICE_NAME)).isEqualTo(t.expectedName);
                      assertThat(resource.getAttribute(SERVICE_VERSION))
                          .isEqualTo(t.expectedVersion);
                    }))
        .collect(Collectors.toList());
  }

  private static InputStream openClasspathResource(String resource) {
    return ManifestResourceExtractorTest.class.getClassLoader().getResourceAsStream(resource);
  }
}
