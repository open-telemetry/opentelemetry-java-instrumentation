/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.network;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.entry;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.semconv.SemanticAttributes;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation") // until old http semconv are dropped in 2.0
class NetworkAttributesExtractorOldSemconvTest {

  static class TestNetworkAttributesGetter
      implements NetworkAttributesGetter<Map<String, String>, Void> {

    @Nullable
    @Override
    public String getNetworkTransport(Map<String, String> request, @Nullable Void response) {
      return request.get("transport");
    }

    @Nullable
    @Override
    public String getNetworkType(Map<String, String> request, @Nullable Void response) {
      return request.get("type");
    }

    @Nullable
    @Override
    public String getNetworkProtocolName(Map<String, String> request, @Nullable Void response) {
      return request.get("protocolName");
    }

    @Nullable
    @Override
    public String getNetworkProtocolVersion(Map<String, String> request, @Nullable Void response) {
      return request.get("protocolVersion");
    }
  }

  @Test
  void allAttributes() {
    Map<String, String> request = new HashMap<>();
    request.put("transport", "TcP");
    request.put("type", "IPv4");
    request.put("protocolName", "Http");
    request.put("protocolVersion", "1.1");

    AttributesExtractor<Map<String, String>, Void> extractor =
        NetworkAttributesExtractor.create(new TestNetworkAttributesGetter());

    AttributesBuilder startAttributes = Attributes.builder();
    extractor.onStart(startAttributes, Context.root(), request);
    assertThat(startAttributes.build()).isEmpty();

    AttributesBuilder endAttributes = Attributes.builder();
    extractor.onEnd(endAttributes, Context.root(), request, null, null);
    assertThat(endAttributes.build())
        .containsOnly(
            // the NetworkAttributesExtractor can't emit old net.transport & net.sock.family
            entry(SemanticAttributes.NET_PROTOCOL_NAME, "http"),
            entry(SemanticAttributes.NET_PROTOCOL_VERSION, "1.1"));
  }

  @Test
  void noAttributes() {
    AttributesExtractor<Map<String, String>, Void> extractor =
        NetworkAttributesExtractor.create(new TestNetworkAttributesGetter());

    AttributesBuilder startAttributes = Attributes.builder();
    extractor.onStart(startAttributes, Context.root(), emptyMap());
    assertThat(startAttributes.build()).isEmpty();

    AttributesBuilder endAttributes = Attributes.builder();
    extractor.onEnd(endAttributes, Context.root(), emptyMap(), null, null);
    assertThat(endAttributes.build()).isEmpty();
  }
}
