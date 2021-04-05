/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NetAttributesExtractorTest {

  static class TestNetAttributesExtractor
      extends NetAttributesExtractor<Map<String, String>, Map<String, String>> {

    @Override
    protected String transport(Map<String, String> request) {
      return request.get("transport");
    }

    @Override
    protected String peerName(Map<String, String> request, Map<String, String> response) {
      return request.get("peerName");
    }

    @Override
    protected Long peerPort(Map<String, String> request, Map<String, String> response) {
      return Long.parseLong(request.get("peerPort"));
    }

    @Override
    protected String peerIp(Map<String, String> request, Map<String, String> response) {
      return request.get("peerIp");
    }
  }

  @Test
  void normal() {
    Map<String, String> request = new HashMap<>();
    request.put("transport", "TCP");
    request.put("peerName", "github.com");
    request.put("peerPort", "123");
    request.put("peerIp", "1.2.3.4");
    AttributesBuilder attributes = Attributes.builder();
    TestNetAttributesExtractor extractor = new TestNetAttributesExtractor();
    extractor.onStart(attributes, request);
    assertThat(attributes.build()).containsOnly(entry(SemanticAttributes.NET_TRANSPORT, "TCP"));
    extractor.onEnd(attributes, request, Collections.emptyMap());
    assertThat(attributes.build())
        .containsOnly(
            entry(SemanticAttributes.NET_TRANSPORT, "TCP"),
            entry(SemanticAttributes.NET_PEER_NAME, "github.com"),
            entry(SemanticAttributes.NET_PEER_PORT, 123L),
            entry(SemanticAttributes.NET_PEER_IP, "1.2.3.4"));
  }
}
