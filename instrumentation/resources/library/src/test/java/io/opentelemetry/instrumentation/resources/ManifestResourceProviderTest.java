/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources;

import static io.opentelemetry.semconv.ResourceAttributes.SERVICE_NAME;
import static io.opentelemetry.semconv.ResourceAttributes.SERVICE_VERSION;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;
import io.opentelemetry.sdk.resources.Resource;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

class ManifestResourceProviderTest {

  @BeforeEach
  void setUp() {
    ResourceDiscoveryCache.resetForTest();
  }

  private static class TestCase {
    private final String name;
    private final String expectedName;
    private final String expectedVersion;
    private final InputStream input;

    public TestCase(String name, String expectedName, String expectedVersion, InputStream input) {
      this.name = name;
      this.expectedName = expectedName;
      this.expectedVersion = expectedVersion;
      this.input = input;
    }
  }

  @TestFactory
  Collection<DynamicTest> createResource() {
    ConfigProperties config = DefaultConfigProperties.createFromMap(Collections.emptyMap());

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
                      ManifestResourceProvider provider =
                          new ManifestResourceProvider(
                              new JarFileDetector(
                                  () -> JarServiceNameDetectorTest.getArgs("app.jar"),
                                  prop -> null,
                                  JarServiceNameDetectorTest::failPath,
                                  p -> {
                                    try {
                                      Manifest manifest = new Manifest();
                                      manifest.read(t.input);
                                      return Optional.of(manifest);
                                    } catch (Exception e) {
                                      return Optional.empty();
                                    }
                                  }));
                      provider.shouldApply(config, Resource.getDefault());

                      Resource resource = provider.createResource(config);
                      assertThat(resource.getAttribute(SERVICE_NAME)).isEqualTo(t.expectedName);
                      assertThat(resource.getAttribute(SERVICE_VERSION))
                          .isEqualTo(t.expectedVersion);
                    }))
        .collect(Collectors.toList());
  }

  private static InputStream openClasspathResource(String resource) {
    return ManifestResourceProviderTest.class.getClassLoader().getResourceAsStream(resource);
  }
}
