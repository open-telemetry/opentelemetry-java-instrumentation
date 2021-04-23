/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.net;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NetRequestAttributesExtractorTest {

  NetRequestAttributesExtractor<Map<String, String>, Void> underTest =
      new NetRequestAttributesExtractor<Map<String, String>, Void>() {
        @Override
        protected String transport(Map<String, String> request) {
          return request.get("transport");
        }

        @Override
        protected String peerName(Map<String, String> request) {
          return request.get("peerName");
        }

        @Override
        protected Long peerPort(Map<String, String> request) {
          return Long.parseLong(request.get("peerPort"));
        }

        @Override
        protected String peerIp(Map<String, String> request) {
          return request.get("peerIp");
        }
      };

  @Test
  void shouldExtractAllAvailableAttributes() {
    // given
    Map<String, String> request = new HashMap<>();
    request.put("transport", "TCP");
    request.put("peerName", "github.com");
    request.put("peerPort", "123");
    request.put("peerIp", "1.2.3.4");

    // when
    AttributesBuilder startAttributes = Attributes.builder();
    underTest.onStart(startAttributes, request);

    AttributesBuilder endAttributes = Attributes.builder();
    underTest.onEnd(endAttributes, request, null);

    // then
    assertThat(startAttributes.build())
        .containsOnly(
            entry(SemanticAttributes.NET_TRANSPORT, "TCP"),
            entry(SemanticAttributes.NET_PEER_NAME, "github.com"),
            entry(SemanticAttributes.NET_PEER_PORT, 123L),
            entry(SemanticAttributes.NET_PEER_IP, "1.2.3.4"));

    assertThat(endAttributes.build().isEmpty()).isTrue();
  }
}
