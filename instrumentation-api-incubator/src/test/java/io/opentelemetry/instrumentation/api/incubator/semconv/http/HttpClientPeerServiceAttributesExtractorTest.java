/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.http;

import static io.opentelemetry.instrumentation.testing.junit.service.SemconvServiceStabilityUtil.maybeStablePeerService;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.semconv.incubating.PeerIncubatingAttributes.PEER_SERVICE;
import static io.opentelemetry.semconv.incubating.ServiceIncubatingAttributes.SERVICE_PEER_NAME;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.entry;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientAttributesGetter;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("deprecation") // testing deprecated class
class HttpClientPeerServiceAttributesExtractorTest {
  @Mock HttpClientAttributesGetter<String, String> httpAttributesExtractor;

  @Test
  void shouldNotSetAnyValueIfNetExtractorReturnsNulls() {
    // given
    io.opentelemetry.instrumentation.api.incubator.semconv.net.PeerServiceResolver
        peerServiceResolver =
            io.opentelemetry.instrumentation.api.incubator.semconv.net.PeerServiceResolver.create(
                singletonMap("1.2.3.4", "myService"));

    AttributesExtractor<String, String> underTest =
        HttpClientPeerServiceAttributesExtractor.create(
            httpAttributesExtractor, peerServiceResolver);

    Context context = Context.root();

    // when
    AttributesBuilder attributes = Attributes.builder();
    underTest.onStart(attributes, context, "request");
    underTest.onEnd(attributes, context, "request", "response", null);

    // then
    assertTrue(attributes.build().isEmpty());
  }

  @Test
  void shouldNotSetAnyValueIfPeerNameDoesNotMatch() {
    // given
    io.opentelemetry.instrumentation.api.incubator.semconv.net.PeerServiceResolver
        peerServiceResolver =
            io.opentelemetry.instrumentation.api.incubator.semconv.net.PeerServiceResolver.create(
                singletonMap("example.com", "myService"));

    AttributesExtractor<String, String> underTest =
        HttpClientPeerServiceAttributesExtractor.create(
            httpAttributesExtractor, peerServiceResolver);

    when(httpAttributesExtractor.getServerAddress(any())).thenReturn("example2.com");

    Context context = Context.root();

    // when
    AttributesBuilder startAttributes = Attributes.builder();
    underTest.onStart(startAttributes, context, "request");
    AttributesBuilder endAttributes = Attributes.builder();
    underTest.onEnd(endAttributes, context, "request", "response", null);

    // then
    assertTrue(startAttributes.build().isEmpty());
    assertTrue(endAttributes.build().isEmpty());
  }

  @Test
  void shouldSetPeerNameIfItMatches() {
    // given
    Map<String, String> peerServiceMapping = new HashMap<>();
    peerServiceMapping.put("example.com", "myService");
    peerServiceMapping.put("1.2.3.4", "someOtherService");

    io.opentelemetry.instrumentation.api.incubator.semconv.net.PeerServiceResolver
        peerServiceResolver =
            io.opentelemetry.instrumentation.api.incubator.semconv.net.PeerServiceResolver.create(
                peerServiceMapping);

    AttributesExtractor<String, String> underTest =
        HttpClientPeerServiceAttributesExtractor.create(
            httpAttributesExtractor, peerServiceResolver);

    when(httpAttributesExtractor.getServerAddress(any())).thenReturn("example.com");

    Context context = Context.root();

    // when
    AttributesBuilder startAttributes = Attributes.builder();
    underTest.onStart(startAttributes, context, "request");
    AttributesBuilder endAttributes = Attributes.builder();
    underTest.onEnd(endAttributes, context, "request", "response", null);

    // then
    assertThat(startAttributes.build()).isEmpty();
    Attributes attrs = endAttributes.build();
    if (SemconvStability.emitOldServicePeerSemconv()
        && SemconvStability.emitStableServicePeerSemconv()) {
      assertThat(attrs)
          .containsOnly(entry(PEER_SERVICE, "myService"), entry(SERVICE_PEER_NAME, "myService"));
    } else {
      assertThat(attrs).containsOnly(entry(maybeStablePeerService(), "myService"));
    }
  }

  @Test
  void shouldFallbackToHostHeaderWhenServerAddressIsNull() {
    // given
    io.opentelemetry.instrumentation.api.incubator.semconv.net.PeerServiceResolver
        peerServiceResolver =
            io.opentelemetry.instrumentation.api.incubator.semconv.net.PeerServiceResolver.create(
                singletonMap("example.com", "myService"));

    AttributesExtractor<String, String> underTest =
        HttpClientPeerServiceAttributesExtractor.create(
            httpAttributesExtractor, peerServiceResolver);

    // server address is null, should fallback to Host header
    when(httpAttributesExtractor.getServerAddress(any())).thenReturn(null);
    when(httpAttributesExtractor.getServerPort(any())).thenReturn(null);
    when(httpAttributesExtractor.getHttpRequestHeader(any(), eq("host")))
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
    if (SemconvStability.emitOldServicePeerSemconv()
        && SemconvStability.emitStableServicePeerSemconv()) {
      assertThat(attrs)
          .containsOnly(entry(PEER_SERVICE, "myService"), entry(SERVICE_PEER_NAME, "myService"));
    } else {
      assertThat(attrs).containsOnly(entry(maybeStablePeerService(), "myService"));
    }
  }
}
