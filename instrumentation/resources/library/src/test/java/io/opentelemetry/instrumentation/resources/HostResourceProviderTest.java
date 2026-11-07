/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources;

import static io.opentelemetry.semconv.incubating.HostIncubatingAttributes.HOST_ID;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static org.mockito.Mockito.mockStatic;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;
import io.opentelemetry.sdk.resources.Resource;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;

class HostResourceProviderTest {

  @ParameterizedTest
  @MethodSource("skipDetectionCases")
  void skipDetection(Map<String, String> properties, Resource existing) {
    Resource expected = HostResource.get();
    try (MockedStatic<HostResource> host = mockStatic(HostResource.class)) {
      host.when(() -> HostResource.create(false)).thenReturn(expected);

      assertThat(createResource(properties, existing)).isEqualTo(expected);
      host.verify(() -> HostResource.create(false));
      host.verifyNoMoreInteractions();
    }
  }

  private static Stream<Arguments> skipDetectionCases() {
    return Stream.of(
        argumentSet(
            "configured host ID",
            singletonMap("otel.resource.attributes", "host.id=configured%20id"),
            Resource.empty()),
        argumentSet(
            "earlier provider host ID",
            emptyMap(),
            Resource.create(Attributes.of(HOST_ID, "cloud-id"))));
  }

  @Test
  void detectsHostIdByDefault() {
    Resource expected = Resource.create(Attributes.of(HOST_ID, "local-id"));
    try (MockedStatic<HostResource> host = mockStatic(HostResource.class)) {
      host.when(() -> HostResource.create(true)).thenReturn(expected);

      Resource resource = createResource(emptyMap(), Resource.empty());

      assertThat(resource).isEqualTo(expected);
      host.verify(() -> HostResource.create(true));
      host.verifyNoMoreInteractions();
    }
  }

  @Test
  void runsAfterCloudProviders() {
    assertThat(new HostResourceProvider().order()).isEqualTo(Integer.MAX_VALUE - 1);
  }

  private static Resource createResource(Map<String, String> properties, Resource existing) {
    ConfigProperties config = DefaultConfigProperties.createFromMap(properties);
    HostResourceProvider provider = new HostResourceProvider();
    assertThat(provider.shouldApply(config, existing)).isTrue();
    return provider.createResource(config);
  }
}
