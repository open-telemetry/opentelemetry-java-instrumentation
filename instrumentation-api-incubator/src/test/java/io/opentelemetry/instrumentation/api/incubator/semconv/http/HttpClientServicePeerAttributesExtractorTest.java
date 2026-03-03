/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.http;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldServicePeerSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableServicePeerSemconv;
import static io.opentelemetry.instrumentation.testing.junit.service.SemconvServiceStabilityUtil.maybeStablePeerService;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.semconv.incubating.PeerIncubatingAttributes.PEER_SERVICE;
import static io.opentelemetry.semconv.incubating.ServiceIncubatingAttributes.SERVICE_PEER_NAME;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.incubator.semconv.service.peer.internal.ServicePeerResolver;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientAttributesGetter;
import io.opentelemetry.instrumentation.api.semconv.http.internal.HostAddressAndPortExtractor;
import io.opentelemetry.instrumentation.api.semconv.network.internal.ServerAddressAndPortExtractor;
import java.util.List;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HttpClientServicePeerAttributesExtractorTest {

  @Mock HttpClientAttributesGetter<String, String> httpAttributesGetter;

  @Test
  void shouldNotSetAnyValueIfNetExtractorReturnsNulls() {
    // given
    AttributesExtractor<String, String> underTest =
        createExtractor(mapping("1.2.3.4", "myService", null));

    Context context = Context.root();

    // when
    AttributesBuilder attributes = Attributes.builder();
    underTest.onStart(attributes, context, "request");
    underTest.onEnd(attributes, context, "request", "response", null);

    // then
    assertThat(attributes.build().isEmpty()).isTrue();
  }

  @Test
  void shouldNotSetAnyValueIfPeerNameDoesNotMatch() {
    // given
    AttributesExtractor<String, String> underTest =
        createExtractor(mapping("example.com", "myService", null));

    when(httpAttributesGetter.getServerAddress(any())).thenReturn("example2.com");

    Context context = Context.root();

    // when
    AttributesBuilder startAttributes = Attributes.builder();
    underTest.onStart(startAttributes, context, "request");
    AttributesBuilder endAttributes = Attributes.builder();
    underTest.onEnd(endAttributes, context, "request", "response", null);

    // then
    assertThat(startAttributes.build().isEmpty()).isTrue();
    assertThat(endAttributes.build().isEmpty()).isTrue();
  }

  @Test
  @SuppressWarnings("deprecation") // using deprecated semconv
  void shouldSetPeerNameIfItMatches() {
    // given
    AttributesExtractor<String, String> underTest =
        createExtractor(
            mapping("example.com", "myService", null),
            mapping("1.2.3.4", "someOtherService", null));

    when(httpAttributesGetter.getServerAddress(any())).thenReturn("example.com");

    Context context = Context.root();

    // when
    AttributesBuilder startAttributes = Attributes.builder();
    underTest.onStart(startAttributes, context, "request");
    AttributesBuilder endAttributes = Attributes.builder();
    underTest.onEnd(endAttributes, context, "request", "response", null);

    // then
    assertThat(startAttributes.build()).isEmpty();
    Attributes attrs = endAttributes.build();
    if (emitOldServicePeerSemconv() && emitStableServicePeerSemconv()) {
      assertThat(attrs)
          .containsOnly(entry(PEER_SERVICE, "myService"), entry(SERVICE_PEER_NAME, "myService"));
    } else {
      assertThat(attrs).containsOnly(entry(maybeStablePeerService(), "myService"));
    }
  }

  @Test
  @SuppressWarnings("deprecation") // using deprecated semconv
  void shouldFallbackToHostHeaderWhenServerAddressIsNull() {
    // given
    AttributesExtractor<String, String> underTest =
        createExtractor(mapping("example.com", "myService", null));

    // server address is null, should fallback to Host header
    when(httpAttributesGetter.getServerAddress(any())).thenReturn(null);
    when(httpAttributesGetter.getServerPort(any())).thenReturn(null);
    when(httpAttributesGetter.getHttpRequestHeader(any(), eq("host")))
        .thenReturn(singletonList("example.com:8080"));

    Context context = Context.root();

    // when
    AttributesBuilder startAttributes = Attributes.builder();
    underTest.onStart(startAttributes, context, "request");
    AttributesBuilder endAttributes = Attributes.builder();
    underTest.onEnd(endAttributes, context, "request", "response", null);

    // then
    assertThat(startAttributes.build()).isEmpty();
    Attributes attrs = endAttributes.build();
    if (emitOldServicePeerSemconv() && emitStableServicePeerSemconv()) {
      assertThat(attrs)
          .containsOnly(entry(PEER_SERVICE, "myService"), entry(SERVICE_PEER_NAME, "myService"));
    } else {
      assertThat(attrs).containsOnly(entry(maybeStablePeerService(), "myService"));
    }
  }

  private AttributesExtractor<String, String> createExtractor(
      DeclarativeConfigProperties... mappings) {
    ServicePeerResolver resolver = createResolver(mappings);
    return new HttpClientServicePeerAttributesExtractor<>(
        new ServerAddressAndPortExtractor<>(
            httpAttributesGetter, new HostAddressAndPortExtractor<>(httpAttributesGetter)),
        httpAttributesGetter,
        resolver);
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
      String peer, @Nullable String serviceName, @Nullable String serviceNamespace) {
    DeclarativeConfigProperties props = mock(DeclarativeConfigProperties.class);
    when(props.getString("peer")).thenReturn(peer);
    when(props.getString("service_name")).thenReturn(serviceName);
    when(props.getString("service_namespace")).thenReturn(serviceNamespace);
    return props;
  }
}
