/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources;

import static io.opentelemetry.semconv.ServiceAttributes.SERVICE_NAME;
import static io.opentelemetry.semconv.ServiceAttributes.SERVICE_VERSION;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;
import io.opentelemetry.sdk.resources.Resource;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ManifestResourceProviderTest {

  @ParameterizedTest(name = "{0}")
  @MethodSource("createResourceCases")
  void createResource(
      String name, String expectedName, String expectedVersion, Resource input, Resource existing) {
    ConfigProperties config = DefaultConfigProperties.createFromMap(emptyMap());

    ManifestResourceProvider provider = new ManifestResourceProvider(() -> input);
    provider.shouldApply(config, existing);

    Resource resource = provider.createResource(config);
    assertThat(resource.getAttribute(SERVICE_NAME)).isEqualTo(expectedName);
    assertThat(resource.getAttribute(SERVICE_VERSION)).isEqualTo(expectedVersion);
  }

  private static Stream<Arguments> createResourceCases() {
    Resource manifest =
        Resource.create(Attributes.of(SERVICE_NAME, "demo", SERVICE_VERSION, "0.0.1-SNAPSHOT"));
    Resource existingWithName = Resource.create(Attributes.of(SERVICE_NAME, "old"));
    return Stream.of(
        arguments("name ok", "demo", "0.0.1-SNAPSHOT", manifest, Resource.getDefault()),
        arguments("name - empty resource", null, null, Resource.empty(), Resource.getDefault()),
        arguments("name already detected", null, "0.0.1-SNAPSHOT", manifest, existingWithName));
  }
}
