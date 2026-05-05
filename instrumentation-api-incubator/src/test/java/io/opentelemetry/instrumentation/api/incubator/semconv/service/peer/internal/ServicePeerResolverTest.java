/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.service.peer.internal;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableServicePeerSemconv;
import static io.opentelemetry.instrumentation.testing.junit.service.SemconvServiceStabilityUtil.maybeStablePeerService;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import java.util.List;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ServicePeerResolverTest {

  private static final AttributeKey<String> SERVICE_PEER_NAMESPACE =
      AttributeKey.stringKey("service.peer.namespace");

  // resolver with a rich set of mappings exercising name, namespace, and specificity
  private static final ServicePeerResolver resolver =
      createResolver(
          mapping("example.com", "baseSvc", "baseNs"),
          mapping("example.com:8080", "portSvc", null),
          mapping("example.com:8080/api", null, "apiNs"),
          mapping("1.2.3.4", "ipSvc", "ipNs"),
          mapping("1.2.3.4/api", "ipApiSvc", null),
          mapping("1.2.3.4:8080/api", "ipPortApi", "ipPortApiNs"),
          mapping("nsonly.com", null, "nsOnly"));

  @ParameterizedTest
  @MethodSource("resolutionCases")
  void shouldResolve(
      String host,
      @Nullable Integer port,
      @Nullable String path,
      @Nullable String expectedName,
      @Nullable String expectedNamespace) {
    AttributesBuilder attrs = Attributes.builder();
    resolver.resolve(host, port, () -> path, attrs::put);
    Attributes result = attrs.build();

    assertName(expectedName, result);
    assertNamespace(expectedNamespace, result);
  }

  static Stream<Arguments> resolutionCases() {
    return Stream.of(
        // basic host-only match returns both name and namespace
        Arguments.of("example.com", null, null, "baseSvc", "baseNs"),
        // host+port mapping wins over host-only; namespace is null for this mapping
        Arguments.of("example.com", 8080, null, "portSvc", null),
        // host+port+path is the most specific; name is null, namespace set
        Arguments.of("example.com", 8080, "/api", null, "apiNs"),
        // non-matching port falls back to host-only
        Arguments.of("example.com", 9090, null, "baseSvc", "baseNs"),
        // matching port but non-matching path falls back to port-only match
        Arguments.of("example.com", 8080, "/other", "portSvc", null),
        // unknown host produces no attributes
        Arguments.of("unknown.com", null, null, null, null),
        // mapping with namespace only (no name)
        Arguments.of("nsonly.com", null, null, null, "nsOnly"),
        // host+path match without port
        Arguments.of("1.2.3.4", null, "/api", "ipApiSvc", null),
        // host+port+path is the most specific match for IP
        Arguments.of("1.2.3.4", 8080, "/api", "ipPortApi", "ipPortApiNs"),
        // port present but no port-only mapping exists; path-only matcher rejects due to port
        // mismatch, falls back to host-only
        Arguments.of("1.2.3.4", 8080, null, "ipSvc", "ipNs"),
        // path-only matcher rejects when query port differs from matcher port
        Arguments.of("1.2.3.4", 9000, "/api", "ipSvc", "ipNs"));
  }

  @Test
  void emptyWhenNoMappings() {
    ServicePeerResolver empty = createResolver();
    assertThat(empty.isEmpty()).isTrue();
  }

  @Test
  void nonEmptyWithValidMapping() {
    assertThat(resolver.isEmpty()).isFalse();
  }

  @Test
  void shouldSkipEntryWithNullPeer() {
    ServicePeerResolver r =
        createResolver(mapping(null, "svc", null), mapping("valid.com", "validSvc", null));

    // null-peer entry is skipped, but resolver is not empty due to the valid entry
    assertThat(r.isEmpty()).isFalse();

    AttributesBuilder attrs = Attributes.builder();
    r.resolve("valid.com", null, () -> null, attrs::put);
    assertName("validSvc", attrs.build());
  }

  @Test
  void shouldSkipEntryWithNullNameAndNamespace() {
    ServicePeerResolver r =
        createResolver(mapping("invalid.com", null, null), mapping("valid.com", "svc", "ns"));

    // invalid.com entry is skipped — resolving it yields no attributes
    AttributesBuilder attrs = Attributes.builder();
    r.resolve("invalid.com", null, () -> null, attrs::put);
    assertThat(attrs.build().isEmpty()).isTrue();

    // valid entry still works
    attrs = Attributes.builder();
    r.resolve("valid.com", null, () -> null, attrs::put);
    assertName("svc", attrs.build());
    assertNamespace("ns", attrs.build());
  }

  private static void assertName(@Nullable String expected, Attributes attrs) {
    AttributeKey<String> key = maybeStablePeerService();
    if (expected != null) {
      assertThat(attrs.get(key)).isEqualTo(expected);
    } else {
      assertThat(attrs.get(key)).isNull();
    }
  }

  private static void assertNamespace(@Nullable String expected, Attributes attrs) {
    if (emitStableServicePeerSemconv()) {
      if (expected != null) {
        assertThat(attrs.get(SERVICE_PEER_NAMESPACE)).isEqualTo(expected);
      } else {
        assertThat(attrs.get(SERVICE_PEER_NAMESPACE)).isNull();
      }
    } else {
      // namespace is never emitted in old semconv mode
      assertThat(attrs.get(SERVICE_PEER_NAMESPACE)).isNull();
    }
  }

  private static ServicePeerResolver createResolver(DeclarativeConfigProperties... entries) {
    ExtendedOpenTelemetry otel = mock(ExtendedOpenTelemetry.class);
    DeclarativeConfigProperties commonConfig = mock(DeclarativeConfigProperties.class);
    when(otel.getInstrumentationConfig("common")).thenReturn(commonConfig);

    List<DeclarativeConfigProperties> entryList = asList(entries);
    when(commonConfig.getStructuredList("service_peer_mapping", emptyList())).thenReturn(entryList);

    return new ServicePeerResolver(otel);
  }

  private static DeclarativeConfigProperties mapping(
      @Nullable String peer, @Nullable String serviceName, @Nullable String serviceNamespace) {
    DeclarativeConfigProperties props = mock(DeclarativeConfigProperties.class);
    when(props.getString("peer")).thenReturn(peer);
    when(props.getString("service_name")).thenReturn(serviceName);
    when(props.getString("service_namespace")).thenReturn(serviceNamespace);
    return props;
  }
}
