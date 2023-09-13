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
class ClientAttributesExtractorOldSemconvTest {

  static class TestClientAttributesGetter
      implements ClientAttributesGetter<Map<String, String>, Void> {

    @Nullable
    @Override
    public String getClientAddress(Map<String, String> request) {
      return request.get("address");
    }

    @Nullable
    @Override
    public Integer getClientPort(Map<String, String> request) {
      String value = request.get("port");
      return value == null ? null : Integer.parseInt(value);
    }

    @Nullable
    @Override
    public String getClientSocketAddress(Map<String, String> request, @Nullable Void response) {
      return request.get("socketAddress");
    }

    @Nullable
    @Override
    public Integer getClientSocketPort(Map<String, String> request, @Nullable Void response) {
      String value = request.get("socketPort");
      return value == null ? null : Integer.parseInt(value);
    }
  }

  @Test
  void allAttributes() {
    Map<String, String> request = new HashMap<>();
    request.put("address", "opentelemetry.io");
    request.put("port", "80");
    request.put("socketAddress", "1.2.3.4");
    request.put("socketPort", "8080");

    AttributesExtractor<Map<String, String>, Void> extractor =
        ClientAttributesExtractor.create(new TestClientAttributesGetter());

    AttributesBuilder startAttributes = Attributes.builder();
    extractor.onStart(startAttributes, Context.root(), request);
    assertThat(startAttributes.build())
        .containsOnly(entry(SemanticAttributes.HTTP_CLIENT_IP, "opentelemetry.io"));

    AttributesBuilder endAttributes = Attributes.builder();
    extractor.onEnd(endAttributes, Context.root(), request, null, null);
    assertThat(endAttributes.build())
        .containsOnly(
            entry(SemanticAttributes.NET_SOCK_PEER_ADDR, "1.2.3.4"),
            entry(SemanticAttributes.NET_SOCK_PEER_PORT, 8080L));
  }

  @Test
  void noAttributes() {
    AttributesExtractor<Map<String, String>, Void> extractor =
        ClientAttributesExtractor.create(new TestClientAttributesGetter());

    AttributesBuilder startAttributes = Attributes.builder();
    extractor.onStart(startAttributes, Context.root(), emptyMap());
    assertThat(startAttributes.build()).isEmpty();

    AttributesBuilder endAttributes = Attributes.builder();
    extractor.onEnd(endAttributes, Context.root(), emptyMap(), null, null);
    assertThat(endAttributes.build()).isEmpty();
  }
}
