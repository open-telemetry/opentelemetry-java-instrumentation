/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.service.internal;

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
import io.opentelemetry.instrumentation.api.incubator.semconv.service.peer.internal.ServicePeerResolver;
import java.util.List;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class ServiceMappingResolverTest {
  // resolver with a rich set of mappings exercising name, namespace, and specificity
  private static final ServiceMappingResolver resolver =
      createResolver(
          mapping("example.com", "baseSvc", "baseNs"),
          mapping("example.com:8080", "portSvc", null),
          mapping("example.com:8080/api", null, "apiNs"),
          mapping("1.2.3.4", "ipSvc", "ipNs"),
          mapping("1.2.3.4/api", "ipApiSvc", null),
          mapping("1.2.3.4:8080/api", "ipPortApi", "ipPortApiNs"),
          mapping("[::1]:8080", "ipv6PortSvc", null),
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
        Arguments.of("1.2.3.4", 9000, "/api", "ipSvc", "ipNs"),
        // bracketed IPv6 mapping matches the IPv6 address and port
        Arguments.of("::1", 8080, null, "ipv6PortSvc", null),
        Arguments.of("::1", 9090, null, null, null));
  }

  @Test
  void emptyWhenNoMappings() {
    ServiceMappingResolver empty = createResolver();
    assertThat(empty.isEmpty()).isTrue();
  }

  @Test
  void nonEmptyWithValidMapping() {
    assertThat(resolver.isEmpty()).isFalse();
  }

  @SuppressWarnings("deprecation")
  @Test
  void deprecatedResolverForwardsToServiceMappingResolver() {
    ServicePeerResolver deprecatedResolver =
        new ServicePeerResolver(createOpenTelemetry(mapping("example.com", "service", null)));
    AttributesBuilder attrs = Attributes.builder();

    deprecatedResolver.resolve("example.com", null, () -> null, attrs::put);

    assertName("service", attrs.build());
  }

  @Test
  void configuredTargetDoesNotAlsoMatchItsFirstHost() {
    String target = "node1:6379,node2:6380";
    ServiceMappingResolver r = createResolver(mapping(target, "cluster", null));
    assertThat(r.isEmpty()).isFalse();

    AttributesBuilder attrs = Attributes.builder();
    r.resolve(target, null, () -> null, attrs::put);
    assertName("cluster", attrs.build());

    attrs = Attributes.builder();
    r.resolve("node1", 6379, () -> null, attrs::put);
    assertThat(attrs.build().isEmpty()).isTrue();
  }

  @Test
  void firstDuplicateMappingWins() {
    ServiceMappingResolver r =
        createResolver(
            mapping("example.com", "first", null), mapping("example.com", "second", null));

    AttributesBuilder attrs = Attributes.builder();
    r.resolve("example.com", null, () -> null, attrs::put);

    assertName("first", attrs.build());
  }

  @Test
  void exactTargetMatchTakesPriorityOverHostPortAndPathMatches() {
    ServiceMappingResolver r =
        createResolver(
            mapping("example.com:8080/api", "exact", null),
            mapping("example.com:8080", "port", null),
            mapping("example.com/api", "path", null));

    AttributesBuilder attrs = Attributes.builder();
    r.resolve("example.com:8080/api", null, () -> null, attrs::put);

    assertName("exact", attrs.build());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "oracle:thin:@(description=(address=(host=h1))(address=(host=h2)))",
        "oracle:thin:@(DESCRIPTION=(ADDRESS = (HOST=h1))(ADDRESS = (HOST=h2)))"
      })
  void oracleDescriptorDoesNotAlsoMatchParsedHost(String target) {
    ServiceMappingResolver r = createResolver(mapping(target, "cluster", null));

    AttributesBuilder attrs = Attributes.builder();
    r.resolve(target, null, () -> null, attrs::put);
    assertName("cluster", attrs.build());

    attrs = Attributes.builder();
    r.resolve("oracle", null, () -> null, attrs::put);
    assertThat(attrs.build().isEmpty()).isTrue();
  }

  @Test
  void commaInPathStillUsesHostAndPathMatching() {
    ServiceMappingResolver r = createResolver(mapping("example.com/api,v2", "versionedApi", null));

    AttributesBuilder attrs = Attributes.builder();
    r.resolve("example.com", null, () -> "/api,v2", attrs::put);

    assertName("versionedApi", attrs.build());
  }

  @Test
  void shouldSkipEntryWithNullTarget() {
    ServiceMappingResolver r =
        createResolver(mapping(null, "svc", null), mapping("valid.com", "validSvc", null));

    // null-target entry is skipped, but resolver is not empty due to the valid entry
    assertThat(r.isEmpty()).isFalse();

    AttributesBuilder attrs = Attributes.builder();
    r.resolve("valid.com", null, () -> null, attrs::put);
    assertName("validSvc", attrs.build());
  }

  @Test
  void shouldSkipEntryWithNullNameAndNamespace() {
    ServiceMappingResolver r =
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
    AttributeKey<String> key = AttributeKey.stringKey("server.service.name");
    if (expected != null) {
      assertThat(attrs.get(key)).isEqualTo(expected);
    } else {
      assertThat(attrs.get(key)).isNull();
    }
  }

  private static void assertNamespace(@Nullable String expected, Attributes attrs) {
    AttributeKey<String> key = AttributeKey.stringKey("server.service.namespace");
    if (expected != null) {
      assertThat(attrs.get(key)).isEqualTo(expected);
    } else {
      assertThat(attrs.get(key)).isNull();
    }
  }

  private static ServiceMappingResolver createResolver(DeclarativeConfigProperties... entries) {
    return new ServiceMappingResolver(createOpenTelemetry(entries));
  }

  private static ExtendedOpenTelemetry createOpenTelemetry(DeclarativeConfigProperties... entries) {
    ExtendedOpenTelemetry otel = mock(ExtendedOpenTelemetry.class);
    DeclarativeConfigProperties commonConfig = mock(DeclarativeConfigProperties.class);
    when(otel.getInstrumentationConfig("common")).thenReturn(commonConfig);

    List<DeclarativeConfigProperties> entryList = asList(entries);
    when(commonConfig.getStructuredList("server_service_mapping", emptyList()))
        .thenReturn(entryList);

    return otel;
  }

  private static DeclarativeConfigProperties mapping(
      @Nullable String target, @Nullable String serviceName, @Nullable String serviceNamespace) {
    DeclarativeConfigProperties props = mock(DeclarativeConfigProperties.class);
    when(props.getString("target")).thenReturn(target);
    when(props.getString("service_name")).thenReturn(serviceName);
    when(props.getString("service_namespace")).thenReturn(serviceNamespace);
    return props;
  }
}
