/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.api.instrumenter;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.entry;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetAttributesOnStartExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PeerServiceAttributesExtractorTest {
  @Mock NetAttributesOnStartExtractor<String, String> netAttributesExtractor;

  @Test
  void shouldNotSetAnyValueIfNetExtractorReturnsNulls() {
    // given
    Map<String, String> peerServiceMapping = singletonMap("1.2.3.4", "myService");

    PeerServiceAttributesExtractor<String, String> underTest =
        new PeerServiceAttributesExtractor<>(peerServiceMapping, netAttributesExtractor);

    // when
    AttributesBuilder attributes = Attributes.builder();
    underTest.onStart(attributes, "request");
    underTest.onEnd(attributes, "request", "response", null);

    // then
    assertTrue(attributes.build().isEmpty());
  }

  @Test
  void shouldNotSetAnyValueIfPeerNameDoesNotMatch() {
    // given
    Map<String, String> peerServiceMapping = singletonMap("example.com", "myService");

    PeerServiceAttributesExtractor<String, String> underTest =
        new PeerServiceAttributesExtractor<>(peerServiceMapping, netAttributesExtractor);

    given(netAttributesExtractor.peerName(any(), any())).willReturn("example2.com");

    // when
    AttributesBuilder startAttributes = Attributes.builder();
    underTest.onStart(startAttributes, "request");
    AttributesBuilder endAttributes = Attributes.builder();
    underTest.onEnd(endAttributes, "request", "response", null);

    // then
    assertTrue(startAttributes.build().isEmpty());
    assertTrue(endAttributes.build().isEmpty());
  }

  @Test
  void shouldNotSetAnyValueIfPeerIpDoesNotMatch() {
    // given
    Map<String, String> peerServiceMapping = singletonMap("1.2.3.4", "myService");

    PeerServiceAttributesExtractor<String, String> underTest =
        new PeerServiceAttributesExtractor<>(peerServiceMapping, netAttributesExtractor);

    given(netAttributesExtractor.peerIp(any(), any())).willReturn("1.2.3.5");

    // when
    AttributesBuilder startAttributes = Attributes.builder();
    underTest.onStart(startAttributes, "request");
    AttributesBuilder endAttributes = Attributes.builder();
    underTest.onEnd(endAttributes, "request", "response", null);

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

    PeerServiceAttributesExtractor<String, String> underTest =
        new PeerServiceAttributesExtractor<>(peerServiceMapping, netAttributesExtractor);

    given(netAttributesExtractor.peerName(any(), any())).willReturn("example.com");

    // when
    AttributesBuilder startAttributes = Attributes.builder();
    underTest.onStart(startAttributes, "request");
    AttributesBuilder endAttributes = Attributes.builder();
    underTest.onEnd(endAttributes, "request", "response", null);

    // then
    assertThat(startAttributes.build())
        .containsOnly(entry(SemanticAttributes.PEER_SERVICE, "myService"));
    assertThat(endAttributes.build())
        .containsOnly(entry(SemanticAttributes.PEER_SERVICE, "myService"));
  }

  @Test
  void shouldSetPeerIpIfItMatchesAndNameDoesNot() {
    // given
    Map<String, String> peerServiceMapping = new HashMap<>();
    peerServiceMapping.put("example.com", "myService");
    peerServiceMapping.put("1.2.3.4", "someOtherService");

    PeerServiceAttributesExtractor<String, String> underTest =
        new PeerServiceAttributesExtractor<>(peerServiceMapping, netAttributesExtractor);

    given(netAttributesExtractor.peerName(any(), any())).willReturn("test.com");
    given(netAttributesExtractor.peerIp(any(), any())).willReturn("1.2.3.4");

    // when
    AttributesBuilder startAttributes = Attributes.builder();
    underTest.onStart(startAttributes, "request");
    AttributesBuilder endAttributes = Attributes.builder();
    underTest.onEnd(endAttributes, "request", "response", null);

    // then
    assertThat(startAttributes.build())
        .containsOnly(entry(SemanticAttributes.PEER_SERVICE, "someOtherService"));
    assertThat(endAttributes.build())
        .containsOnly(entry(SemanticAttributes.PEER_SERVICE, "someOtherService"));
  }
}
