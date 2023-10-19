/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.net;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.semconv.SemanticAttributes.NetTransportValues.IP_TCP;
import static org.assertj.core.api.Assertions.entry;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.network.internal.NetworkAttributes;
import io.opentelemetry.semconv.SemanticAttributes;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation") // testing deprecated class
class NetClientAttributesExtractorBothSemconvTest {

  static class TestNetClientAttributesGetter
      implements NetClientAttributesGetter<Map<String, String>, Map<String, String>> {

    @Override
    public String getTransport(Map<String, String> request, Map<String, String> response) {
      return response.get("netTransport");
    }

    @Nullable
    @Override
    public String getNetworkTransport(
        Map<String, String> request, @Nullable Map<String, String> response) {
      return request.get("transport");
    }

    @Nullable
    @Override
    public String getNetworkType(
        Map<String, String> request, @Nullable Map<String, String> response) {
      return request.get("type");
    }

    @Nullable
    @Override
    public String getNetworkProtocolName(
        Map<String, String> request, @Nullable Map<String, String> response) {
      return request.get("protocolName");
    }

    @Nullable
    @Override
    public String getNetworkProtocolVersion(
        Map<String, String> request, @Nullable Map<String, String> response) {
      return request.get("protocolVersion");
    }

    @Override
    public String getServerAddress(Map<String, String> request) {
      return request.get("peerName");
    }

    @Override
    public Integer getServerPort(Map<String, String> request) {
      String peerPort = request.get("peerPort");
      return peerPort == null ? null : Integer.valueOf(peerPort);
    }

    @Override
    public String getSockFamily(Map<String, String> request, Map<String, String> response) {
      return response.get("sockFamily");
    }

    @Override
    public String getNetworkPeerAddress(Map<String, String> request, Map<String, String> response) {
      return response.get("sockPeerAddr");
    }

    @Override
    public Integer getNetworkPeerPort(Map<String, String> request, Map<String, String> response) {
      String sockPeerPort = response.get("sockPeerPort");
      return sockPeerPort == null ? null : Integer.valueOf(sockPeerPort);
    }
  }

  private final AttributesExtractor<Map<String, String>, Map<String, String>> extractor =
      NetClientAttributesExtractor.create(new TestNetClientAttributesGetter());

  @Test
  void normal() {
    // given
    Map<String, String> map = new HashMap<>();
    map.put("netTransport", IP_TCP);
    map.put("transport", "tcp");
    map.put("type", "ipv6");
    map.put("protocolName", "http");
    map.put("protocolVersion", "1.1");
    map.put("peerName", "opentelemetry.io");
    map.put("peerPort", "42");
    map.put("sockFamily", "inet6");
    map.put("sockPeerAddr", "1:2:3:4::");
    map.put("sockPeerName", "proxy.opentelemetry.io");
    map.put("sockPeerPort", "123");

    Context context = Context.root();

    // when
    AttributesBuilder startAttributes = Attributes.builder();
    extractor.onStart(startAttributes, context, map);

    AttributesBuilder endAttributes = Attributes.builder();
    extractor.onEnd(endAttributes, context, map, map, null);

    // then
    assertThat(startAttributes.build())
        .containsOnly(
            entry(SemanticAttributes.NET_PEER_NAME, "opentelemetry.io"),
            entry(SemanticAttributes.NET_PEER_PORT, 42L),
            entry(SemanticAttributes.SERVER_ADDRESS, "opentelemetry.io"),
            entry(SemanticAttributes.SERVER_PORT, 42L));

    assertThat(endAttributes.build())
        .containsOnly(
            entry(SemanticAttributes.NET_TRANSPORT, IP_TCP),
            entry(SemanticAttributes.NET_PROTOCOL_NAME, "http"),
            entry(SemanticAttributes.NET_PROTOCOL_VERSION, "1.1"),
            entry(SemanticAttributes.NETWORK_TRANSPORT, "tcp"),
            entry(SemanticAttributes.NETWORK_TYPE, "ipv6"),
            entry(SemanticAttributes.NETWORK_PROTOCOL_NAME, "http"),
            entry(SemanticAttributes.NETWORK_PROTOCOL_VERSION, "1.1"),
            entry(SemanticAttributes.NET_SOCK_FAMILY, "inet6"),
            entry(SemanticAttributes.NET_SOCK_PEER_ADDR, "1:2:3:4::"),
            entry(SemanticAttributes.NET_SOCK_PEER_PORT, 123L),
            entry(NetworkAttributes.NETWORK_PEER_ADDRESS, "1:2:3:4::"),
            entry(NetworkAttributes.NETWORK_PEER_PORT, 123L));
  }
}
