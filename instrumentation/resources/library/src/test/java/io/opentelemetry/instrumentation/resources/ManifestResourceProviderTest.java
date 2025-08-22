/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources;

import static io.opentelemetry.semconv.ServiceAttributes.SERVICE_NAME;
import static io.opentelemetry.semconv.ServiceAttributes.SERVICE_VERSION;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;
import io.opentelemetry.sdk.resources.Resource;
import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

class ManifestResourceProviderTest {

  private static class TestCase {
    private final String name;
    private final String expectedName;
    private final String expectedVersion;
    private final Resource input;
    private final Resource existing;

    TestCase(
        String name,
        String expectedName,
        String expectedVersion,
        Resource input,
        Resource existing) {
      this.name = name;
      this.expectedName = expectedName;
      this.expectedVersion = expectedVersion;
      this.input = input;
      this.existing = existing;
    }
  }

  @TestFactory
  Collection<DynamicTest> createResource() {
    ConfigProperties config = DefaultConfigProperties.createFromMap(Collections.emptyMap());

    return Stream.of(
            new TestCase(
                "name ok",
                "demo",
                "0.0.1-SNAPSHOT",
                Resource.create(
                    Attributes.of(SERVICE_NAME, "demo", SERVICE_VERSION, "0.0.1-SNAPSHOT")),
                Resource.getDefault()),
            new TestCase(
                "name - empty resource", null, null, Resource.empty(), Resource.getDefault()),
            new TestCase(
                "name already detected",
                null,
                "0.0.1-SNAPSHOT",
                Resource.create(
                    Attributes.of(SERVICE_NAME, "demo", SERVICE_VERSION, "0.0.1-SNAPSHOT")),
                Resource.create(Attributes.of(SERVICE_NAME, "old"))))
        .map(
            t ->
                DynamicTest.dynamicTest(
                    t.name,
                    () -> {
                      ManifestResourceProvider provider =
                          new ManifestResourceProvider(() -> t.input);
                      provider.shouldApply(config, t.existing);

                      Resource resource = provider.createResource(config);
                      assertThat(resource.getAttribute(SERVICE_NAME)).isEqualTo(t.expectedName);
                      assertThat(resource.getAttribute(SERVICE_VERSION))
                          .isEqualTo(t.expectedVersion);
                    }))
        .collect(Collectors.toList());
  }
}
