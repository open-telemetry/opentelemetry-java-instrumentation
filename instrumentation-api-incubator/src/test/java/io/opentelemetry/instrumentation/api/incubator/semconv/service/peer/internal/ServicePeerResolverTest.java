/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.service.peer.internal;

import static io.opentelemetry.instrumentation.testing.junit.service.SemconvServiceStabilityUtil.maybeStablePeerService;
import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;

class ServicePeerResolverTest {

  @Test
  void test() {
    ServicePeerResolver resolver =
        createResolver(
            mapping("example.com:8080", "myService", null),
            mapping("example.com", "myServiceBase", null),
            mapping("1.2.3.4", "someOtherService", null),
            mapping("1.2.3.4:8080/api", "someOtherService8080", null),
            mapping("1.2.3.4/api", "someOtherServiceAPI", null));

    assertEquals("myServiceBase", resolveServiceName(resolver, "example.com", null, null));
    assertEquals("myService", resolveServiceName(resolver, "example.com", 8080, "/"));
    assertEquals("someOtherService8080", resolveServiceName(resolver, "1.2.3.4", 8080, "/api"));
    assertEquals("someOtherService", resolveServiceName(resolver, "1.2.3.4", 9000, "/api"));
    assertEquals("someOtherService", resolveServiceName(resolver, "1.2.3.4", 8080, null));
    assertEquals("someOtherServiceAPI", resolveServiceName(resolver, "1.2.3.4", null, "/api"));
  }

  @Test
  void shouldReturnNullForUnknownHost() {
    ServicePeerResolver resolver = createResolver(mapping("example.com", "myService", null));

    assertNull(resolveServiceName(resolver, "unknown.com", null, null));
  }

  @Test
  void shouldBeEmptyWithNoMappings() {
    ServicePeerResolver resolver = createResolver();

    assertTrue(resolver.isEmpty());
  }

  @Nullable
  private static String resolveServiceName(
      ServicePeerResolver resolver, String host, @Nullable Integer port, @Nullable String path) {
    AttributesBuilder builder = Attributes.builder();
    resolver.resolve(host, port, () -> path, builder::put);
    return builder.build().get(maybeStablePeerService());
  }

  private static ServicePeerResolver createResolver(DeclarativeConfigProperties... entries) {
    ExtendedOpenTelemetry otel = mock(ExtendedOpenTelemetry.class);
    DeclarativeConfigProperties commonConfig = mock(DeclarativeConfigProperties.class);
    when(otel.getInstrumentationConfig("common")).thenReturn(commonConfig);
    List<DeclarativeConfigProperties> entryList = Arrays.asList(entries);
    when(commonConfig.getStructuredList("service_peer_mapping", emptyList())).thenReturn(entryList);
    return new ServicePeerResolver(otel);
  }

  private static DeclarativeConfigProperties mapping(
      String peer, @Nullable String serviceName, @Nullable String serviceNamespace) {
    DeclarativeConfigProperties props = mock(DeclarativeConfigProperties.class);
    when(props.getString("peer")).thenReturn(peer);
    when(props.getString("service_name")).thenReturn(serviceName);
    when(props.getString("service_namespace")).thenReturn(serviceNamespace);
    return props;
  }
}
