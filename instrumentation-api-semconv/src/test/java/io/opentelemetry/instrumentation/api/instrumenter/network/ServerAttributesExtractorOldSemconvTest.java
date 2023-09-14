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
class ServerAttributesExtractorOldSemconvTest {

  static class TestServerAttributesGetter
      implements ServerAttributesGetter<Map<String, String>, Void> {

    @Nullable
    @Override
    public String getServerAddress(Map<String, String> request) {
      return request.get("address");
    }

    @Nullable
    @Override
    public Integer getServerPort(Map<String, String> request) {
      String port = request.get("port");
      return port == null ? null : Integer.parseInt(port);
    }

    @Nullable
    @Override
    public String getServerSocketDomain(Map<String, String> request, @Nullable Void response) {
      return request.get("socketDomain");
    }

    @Nullable
    @Override
    public String getServerSocketAddress(Map<String, String> request, @Nullable Void response) {
      return request.get("socketAddress");
    }

    @Nullable
    @Override
    public Integer getServerSocketPort(Map<String, String> request, @Nullable Void response) {
      String port = request.get("socketPort");
      return port == null ? null : Integer.parseInt(port);
    }
  }

  @Test
  void allAttributes_peer() {
    Map<String, String> request = new HashMap<>();
    request.put("address", "opentelemetry.io");
    request.put("port", "80");
    request.put("socketDomain", "proxy.opentelemetry.io");
    request.put("socketAddress", "1.2.3.4");
    request.put("socketPort", "8080");

    AttributesExtractor<Map<String, String>, Void> extractor =
        ServerAttributesExtractor.create(new TestServerAttributesGetter());

    AttributesBuilder startAttributes = Attributes.builder();
    extractor.onStart(startAttributes, Context.root(), request);
    assertThat(startAttributes.build())
        .containsOnly(
            entry(SemanticAttributes.NET_PEER_NAME, "opentelemetry.io"),
            entry(SemanticAttributes.NET_PEER_PORT, 80L));

    AttributesBuilder endAttributes = Attributes.builder();
    extractor.onEnd(endAttributes, Context.root(), request, null, null);
    assertThat(endAttributes.build())
        .containsOnly(
            entry(SemanticAttributes.NET_SOCK_PEER_NAME, "proxy.opentelemetry.io"),
            entry(SemanticAttributes.NET_SOCK_PEER_ADDR, "1.2.3.4"),
            entry(SemanticAttributes.NET_SOCK_PEER_PORT, 8080L));
  }

  @SuppressWarnings("deprecation") // need to test the old semconv too
  @Test
  void allAttributes_host() {
    Map<String, String> request = new HashMap<>();
    request.put("address", "opentelemetry.io");
    request.put("port", "80");
    request.put("socketDomain", "proxy.opentelemetry.io");
    request.put("socketAddress", "1.2.3.4");
    request.put("socketPort", "8080");

    AttributesExtractor<Map<String, String>, Void> extractor =
        ServerAttributesExtractor.createForServerSide(new TestServerAttributesGetter());

    AttributesBuilder startAttributes = Attributes.builder();
    extractor.onStart(startAttributes, Context.root(), request);
    assertThat(startAttributes.build())
        .containsOnly(
            entry(SemanticAttributes.NET_HOST_NAME, "opentelemetry.io"),
            entry(SemanticAttributes.NET_HOST_PORT, 80L));

    AttributesBuilder endAttributes = Attributes.builder();
    extractor.onEnd(endAttributes, Context.root(), request, null, null);
    assertThat(endAttributes.build())
        .containsOnly(
            entry(SemanticAttributes.NET_SOCK_HOST_ADDR, "1.2.3.4"),
            entry(SemanticAttributes.NET_SOCK_HOST_PORT, 8080L));
  }

  @Test
  void noAttributes() {
    AttributesExtractor<Map<String, String>, Void> extractor =
        ServerAttributesExtractor.create(new TestServerAttributesGetter());

    AttributesBuilder startAttributes = Attributes.builder();
    extractor.onStart(startAttributes, Context.root(), emptyMap());
    assertThat(startAttributes.build()).isEmpty();

    AttributesBuilder endAttributes = Attributes.builder();
    extractor.onEnd(endAttributes, Context.root(), emptyMap(), null, null);
    assertThat(endAttributes.build()).isEmpty();
  }
}
