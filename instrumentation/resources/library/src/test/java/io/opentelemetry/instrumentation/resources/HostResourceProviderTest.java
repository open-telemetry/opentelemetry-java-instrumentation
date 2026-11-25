/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources;

import static io.opentelemetry.semconv.incubating.HostIncubatingAttributes.HOST_ID;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;
import io.opentelemetry.sdk.resources.Resource;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class HostResourceProviderTest {

  @Test
  void skipsHostIdDetectionWhenConfigured() {
    ConfigProperties config =
        DefaultConfigProperties.createFromMap(
            singletonMap("otel.resource.attributes", "host.id=configured%20id"));
    Resource expected = HostResource.get();
    try (MockedStatic<HostResource> host = mockStatic(HostResource.class)) {
      host.when(() -> HostResource.create(false)).thenReturn(expected);

      assertThat(new HostResourceProvider().createResource(config)).isEqualTo(expected);
      host.verify(() -> HostResource.create(false));
      host.verifyNoMoreInteractions();
    }
  }

  @Test
  void detectsHostIdByDefault() {
    ConfigProperties config = DefaultConfigProperties.createFromMap(emptyMap());
    Resource expected = Resource.create(Attributes.of(HOST_ID, "local-id"));
    try (MockedStatic<HostResource> host = mockStatic(HostResource.class)) {
      host.when(() -> HostResource.create(true)).thenReturn(expected);

      assertThat(new HostResourceProvider().createResource(config)).isEqualTo(expected);
      host.verify(() -> HostResource.create(true));
      host.verifyNoMoreInteractions();
    }
  }
}
