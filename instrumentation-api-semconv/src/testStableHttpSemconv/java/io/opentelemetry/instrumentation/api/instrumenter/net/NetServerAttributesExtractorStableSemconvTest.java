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
class NetServerAttributesExtractorStableSemconvTest {

  static class TestNetServerAttributesGetter
      implements NetServerAttributesGetter<Map<String, String>, Void> {

    @Override
    public String getTransport(Map<String, String> request) {
      return request.get("netTransport");
    }

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
    public String getNetworkProtocolName(Map<String, String> request, Void response) {
      return request.get("protocolName");
    }

    @Nullable
    @Override
    public String getNetworkProtocolVersion(Map<String, String> request, Void response) {
      return request.get("protocolVersion");
    }

    @Nullable
    @Override
    public String getServerAddress(Map<String, String> request) {
      return request.get("hostName");
    }

    @Nullable
    @Override
    public Integer getServerPort(Map<String, String> request) {
      String hostPort = request.get("hostPort");
      return hostPort == null ? null : Integer.valueOf(hostPort);
    }

    @Nullable
    @Override
    public String getSockFamily(Map<String, String> request) {
      return request.get("sockFamily");
    }

    @Override
    public String getNetworkPeerAddress(Map<String, String> request, Void response) {
      return request.get("sockPeerAddr");
    }

    @Override
    public Integer getNetworkPeerPort(Map<String, String> request, Void response) {
      String sockPeerPort = request.get("sockPeerPort");
      return sockPeerPort == null ? null : Integer.valueOf(sockPeerPort);
    }

    @Nullable
    @Override
    public String getNetworkLocalAddress(Map<String, String> request, Void response) {
      return request.get("sockHostAddr");
    }

    @Nullable
    @Override
    public Integer getNetworkLocalPort(Map<String, String> request, Void response) {
      String sockHostPort = request.get("sockHostPort");
      return sockHostPort == null ? null : Integer.valueOf(sockHostPort);
    }
  }

  AttributesExtractor<Map<String, String>, Void> extractor =
      NetServerAttributesExtractor.create(new TestNetServerAttributesGetter());

  @Test
  void normal() {
    // given
    Map<String, String> map = new HashMap<>();
    map.put("netTransport", IP_TCP);
    map.put("transport", "tcp");
    map.put("type", "ipv6");
    map.put("protocolName", "http");
    map.put("protocolVersion", "1.1");
    map.put("hostName", "opentelemetry.io");
    map.put("hostPort", "80");
    map.put("sockFamily", "inet6");
    map.put("sockPeerAddr", "1:2:3:4::");
    map.put("sockPeerPort", "42");
    map.put("sockHostAddr", "4:3:2:1::");
    map.put("sockHostPort", "8080");

    Context context = Context.root();

    // when
    AttributesBuilder startAttributes = Attributes.builder();
    extractor.onStart(startAttributes, context, map);

    AttributesBuilder endAttributes = Attributes.builder();
    extractor.onEnd(endAttributes, context, map, null, null);

    // then
    assertThat(startAttributes.build())
        .containsOnly(
            entry(SemanticAttributes.SERVER_ADDRESS, "opentelemetry.io"),
            entry(SemanticAttributes.SERVER_PORT, 80L));

    assertThat(endAttributes.build())
        .containsOnly(
            entry(SemanticAttributes.NETWORK_TRANSPORT, "tcp"),
            entry(SemanticAttributes.NETWORK_TYPE, "ipv6"),
            entry(SemanticAttributes.NETWORK_PROTOCOL_NAME, "http"),
            entry(SemanticAttributes.NETWORK_PROTOCOL_VERSION, "1.1"),
            entry(NetworkAttributes.NETWORK_LOCAL_ADDRESS, "4:3:2:1::"),
            entry(NetworkAttributes.NETWORK_LOCAL_PORT, 8080L),
            entry(NetworkAttributes.NETWORK_PEER_ADDRESS, "1:2:3:4::"),
            entry(NetworkAttributes.NETWORK_PEER_PORT, 42L));
  }
}
