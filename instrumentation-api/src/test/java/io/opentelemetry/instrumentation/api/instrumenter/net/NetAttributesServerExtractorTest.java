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

class NetAttributesServerExtractorTest {

  static class TestNetAttributesServerExtractor
      extends NetAttributesServerExtractor<Map<String, String>, Map<String, String>> {

    @Override
    public String transport(Map<String, String> request) {
      return request.get("transport");
    }

    @Override
    public String peerName(Map<String, String> request) {
      return request.get("peerName");
    }

    @Override
    public Integer peerPort(Map<String, String> request) {
      return Integer.valueOf(request.get("peerPort"));
    }

    @Override
    public String peerIp(Map<String, String> request) {
      return request.get("peerIp");
    }
  }

  @Test
  void normal() {
    // given
    Map<String, String> request = new HashMap<>();
    request.put("transport", "TCP");
    request.put("peerName", "github.com");
    request.put("peerPort", "123");
    request.put("peerIp", "1.2.3.4");

    Map<String, String> response = new HashMap<>();
    response.put("peerName", "opentelemetry.io");
    response.put("peerPort", "42");
    response.put("peerIp", "4.3.2.1");

    TestNetAttributesServerExtractor extractor = new TestNetAttributesServerExtractor();

    // when
    AttributesBuilder startAttributes = Attributes.builder();
    extractor.onStart(startAttributes, request);

    AttributesBuilder endAttributes = Attributes.builder();
    extractor.onEnd(endAttributes, request, response, null);

    // then
    assertThat(startAttributes.build())
        .containsOnly(
            entry(SemanticAttributes.NET_TRANSPORT, "TCP"),
            entry(SemanticAttributes.NET_PEER_NAME, "github.com"),
            entry(SemanticAttributes.NET_PEER_PORT, 123L),
            entry(SemanticAttributes.NET_PEER_IP, "1.2.3.4"));

    assertThat(endAttributes.build()).isEmpty();
  }

  @Test
  public void doesNotSetDuplicateAttributes() {
    // given
    Map<String, String> request = new HashMap<>();
    request.put("transport", "TCP");
    request.put("peerName", "1.2.3.4");
    request.put("peerIp", "1.2.3.4");
    request.put("peerPort", "123");

    Map<String, String> response = new HashMap<>();
    response.put("peerName", "4.3.2.1");
    response.put("peerPort", "42");
    response.put("peerIp", "4.3.2.1");

    TestNetAttributesServerExtractor extractor = new TestNetAttributesServerExtractor();

    // when
    AttributesBuilder startAttributes = Attributes.builder();
    extractor.onStart(startAttributes, request);

    AttributesBuilder endAttributes = Attributes.builder();
    extractor.onEnd(endAttributes, request, response, null);

    // then
    assertThat(startAttributes.build())
        .containsOnly(
            entry(SemanticAttributes.NET_TRANSPORT, "TCP"),
            entry(SemanticAttributes.NET_PEER_PORT, 123L),
            entry(SemanticAttributes.NET_PEER_IP, "1.2.3.4"));

    assertThat(endAttributes.build()).isEmpty();
  }
}
