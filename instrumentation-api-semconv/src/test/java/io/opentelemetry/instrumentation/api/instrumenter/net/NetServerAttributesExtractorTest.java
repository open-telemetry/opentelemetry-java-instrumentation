/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.net;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NetServerAttributesExtractorTest {

  static class TestNetServerAttributesGetter
      implements NetServerAttributesGetter<Map<String, String>> {

    @Override
    public String transport(Map<String, String> request) {
      return request.get("transport");
    }

    @Override
    public Integer sockPeerPort(Map<String, String> request) {
      return Integer.valueOf(request.get("sockPeerPort"));
    }

    @Override
    public String sockPeerAddr(Map<String, String> request) {
      return request.get("sockPeerAddr");
    }
  }

  @Test
  void normal() {
    // given
    Map<String, String> request = new HashMap<>();
    request.put("transport", "TCP");
    request.put("sockPeerPort", "123");
    request.put("sockPeerAddr", "1.2.3.4");

    Map<String, String> response = new HashMap<>();
    response.put("sockPeerPort", "42");
    response.put("sockPeerAddr", "4.3.2.1");

    NetServerAttributesExtractor<Map<String, String>, Map<String, String>> extractor =
        createTestExtractor();

    Context context = Context.root();

    // when
    AttributesBuilder startAttributes = Attributes.builder();
    extractor.onStart(startAttributes, context, request);

    AttributesBuilder endAttributes = Attributes.builder();
    extractor.onEnd(endAttributes, context, request, response, null);

    // then
    assertThat(startAttributes.build())
        .containsOnly(
            entry(SemanticAttributes.NET_TRANSPORT, "TCP"),
            entry(AttributeKey.longKey("net.sock.peer.port"), 123L),
            entry(AttributeKey.stringKey("net.sock.peer.addr"), "1.2.3.4"));

    assertThat(endAttributes.build()).isEmpty();
  }

  @Test
  public void doesNotSetDuplicateAttributes() {
    // given
    Map<String, String> request = new HashMap<>();
    request.put("transport", "TCP");
    request.put("sockPeerAddr", "1.2.3.4");
    request.put("sockPeerPort", "123");

    Map<String, String> response = new HashMap<>();
    response.put("sockPeerPort", "42");
    response.put("sockPeerAddr", "4.3.2.1");

    NetServerAttributesExtractor<Map<String, String>, Map<String, String>> extractor =
        createTestExtractor();

    Context context = Context.root();

    // when
    AttributesBuilder startAttributes = Attributes.builder();
    extractor.onStart(startAttributes, context, request);

    AttributesBuilder endAttributes = Attributes.builder();
    extractor.onEnd(endAttributes, context, request, response, null);

    // then
    assertThat(startAttributes.build())
        .containsOnly(
            entry(SemanticAttributes.NET_TRANSPORT, "TCP"),
            entry(AttributeKey.longKey("net.sock.peer.port"), 123L),
            entry(AttributeKey.stringKey("net.sock.peer.addr"), "1.2.3.4"));

    assertThat(endAttributes.build()).isEmpty();
  }

  @Test
  public void doesNotSetNegativePort() {
    // given
    Map<String, String> request = new HashMap<>();
    request.put("sockPeerPort", "-42");

    Map<String, String> response = new HashMap<>();
    response.put("sockPeerPort", "-1");

    NetServerAttributesExtractor<Map<String, String>, Map<String, String>> extractor =
        createTestExtractor();

    Context context = Context.root();

    // when
    AttributesBuilder startAttributes = Attributes.builder();
    extractor.onStart(startAttributes, context, request);

    AttributesBuilder endAttributes = Attributes.builder();
    extractor.onEnd(endAttributes, context, request, response, null);

    // then
    assertThat(startAttributes.build()).isEmpty();
    assertThat(endAttributes.build()).isEmpty();
  }

  private static NetServerAttributesExtractor<Map<String, String>, Map<String, String>>
      createTestExtractor() {
    return NetServerAttributesExtractor.create(new TestNetServerAttributesGetter());
  }
}
