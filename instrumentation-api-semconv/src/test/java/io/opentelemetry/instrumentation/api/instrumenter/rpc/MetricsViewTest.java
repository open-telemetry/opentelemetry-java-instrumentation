/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.rpc;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.instrumentation.api.instrumenter.rpc.MetricsView.applyClientView;
import static io.opentelemetry.instrumentation.api.instrumenter.rpc.MetricsView.applyServerView;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.instrumentation.api.instrumenter.network.internal.NetworkAttributes;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.junit.jupiter.api.Test;

class MetricsViewTest {

  @Test
  void shouldApplyClientView() {
    Attributes startAttributes =
        Attributes.builder()
            .put(SemanticAttributes.RPC_SYSTEM, "grpc")
            .put(SemanticAttributes.RPC_SERVICE, "myservice.EchoService")
            .put(SemanticAttributes.RPC_METHOD, "exampleMethod")
            .put(stringKey("one"), "1")
            .build();

    Attributes endAttributes =
        Attributes.builder()
            .put(SemanticAttributes.NET_PEER_NAME, "example.com")
            .put(SemanticAttributes.NET_PEER_PORT, 8080)
            .put(SemanticAttributes.NET_TRANSPORT, "ip_tcp")
            .put(stringKey("two"), "2")
            .build();

    assertThat(applyClientView(startAttributes, endAttributes))
        .containsOnly(
            entry(SemanticAttributes.RPC_SYSTEM, "grpc"),
            entry(SemanticAttributes.RPC_SERVICE, "myservice.EchoService"),
            entry(SemanticAttributes.RPC_METHOD, "exampleMethod"),
            entry(SemanticAttributes.NET_PEER_NAME, "example.com"),
            entry(SemanticAttributes.NET_PEER_PORT, 8080L),
            entry(SemanticAttributes.NET_TRANSPORT, "ip_tcp"));
  }

  @Test
  void shouldApplyClientView_stableHttpSemconv() {
    Attributes startAttributes =
        Attributes.builder()
            .put(SemanticAttributes.RPC_SYSTEM, "grpc")
            .put(SemanticAttributes.RPC_SERVICE, "myservice.EchoService")
            .put(SemanticAttributes.RPC_METHOD, "exampleMethod")
            .put(stringKey("one"), "1")
            .build();

    Attributes endAttributes =
        Attributes.builder()
            .put(NetworkAttributes.SERVER_ADDRESS, "example.com")
            .put(NetworkAttributes.SERVER_PORT, 8080)
            .put(NetworkAttributes.SERVER_SOCKET_ADDRESS, "127.0.0.1")
            .put(NetworkAttributes.SERVER_SOCKET_PORT, 12345)
            .put(NetworkAttributes.NETWORK_TYPE, "ipv4")
            .put(NetworkAttributes.NETWORK_TRANSPORT, "tcp")
            .put(stringKey("two"), "2")
            .build();

    assertThat(applyClientView(startAttributes, endAttributes))
        .containsOnly(
            entry(SemanticAttributes.RPC_SYSTEM, "grpc"),
            entry(SemanticAttributes.RPC_SERVICE, "myservice.EchoService"),
            entry(SemanticAttributes.RPC_METHOD, "exampleMethod"),
            entry(NetworkAttributes.SERVER_ADDRESS, "example.com"),
            entry(NetworkAttributes.SERVER_PORT, 8080L),
            entry(NetworkAttributes.SERVER_SOCKET_ADDRESS, "127.0.0.1"),
            entry(NetworkAttributes.SERVER_SOCKET_PORT, 12345L),
            entry(NetworkAttributes.NETWORK_TYPE, "ipv4"),
            entry(NetworkAttributes.NETWORK_TRANSPORT, "tcp"));
  }

  @Test
  void shouldApplyServerView() {
    Attributes startAttributes =
        Attributes.builder()
            .put(SemanticAttributes.RPC_SYSTEM, "grpc")
            .put(SemanticAttributes.RPC_SERVICE, "myservice.EchoService")
            .put(SemanticAttributes.RPC_METHOD, "exampleMethod")
            .put(stringKey("one"), "1")
            .build();

    Attributes endAttributes =
        Attributes.builder()
            .put(SemanticAttributes.NET_HOST_NAME, "example.com")
            .put(SemanticAttributes.NET_SOCK_HOST_ADDR, "127.0.0.1")
            .put(SemanticAttributes.NET_HOST_PORT, 8080)
            .put(SemanticAttributes.NET_TRANSPORT, "ip_tcp")
            .put(stringKey("two"), "2")
            .build();

    assertThat(applyServerView(startAttributes, endAttributes))
        .containsOnly(
            entry(SemanticAttributes.RPC_SYSTEM, "grpc"),
            entry(SemanticAttributes.RPC_SERVICE, "myservice.EchoService"),
            entry(SemanticAttributes.RPC_METHOD, "exampleMethod"),
            entry(SemanticAttributes.NET_HOST_NAME, "example.com"),
            entry(SemanticAttributes.NET_TRANSPORT, "ip_tcp"));
  }

  @Test
  void shouldApplyServerView_stableHttpSemconv() {
    Attributes startAttributes =
        Attributes.builder()
            .put(SemanticAttributes.RPC_SYSTEM, "grpc")
            .put(SemanticAttributes.RPC_SERVICE, "myservice.EchoService")
            .put(SemanticAttributes.RPC_METHOD, "exampleMethod")
            .put(stringKey("one"), "1")
            .build();

    Attributes endAttributes =
        Attributes.builder()
            .put(NetworkAttributes.SERVER_ADDRESS, "example.com")
            .put(NetworkAttributes.SERVER_PORT, 8080)
            .put(NetworkAttributes.SERVER_SOCKET_ADDRESS, "127.0.0.1")
            .put(NetworkAttributes.SERVER_SOCKET_PORT, 12345)
            .put(NetworkAttributes.NETWORK_TYPE, "ipv4")
            .put(NetworkAttributes.NETWORK_TRANSPORT, "tcp")
            .put(stringKey("two"), "2")
            .build();

    assertThat(applyServerView(startAttributes, endAttributes))
        .containsOnly(
            entry(SemanticAttributes.RPC_SYSTEM, "grpc"),
            entry(SemanticAttributes.RPC_SERVICE, "myservice.EchoService"),
            entry(SemanticAttributes.RPC_METHOD, "exampleMethod"),
            entry(NetworkAttributes.SERVER_ADDRESS, "example.com"),
            entry(NetworkAttributes.SERVER_PORT, 8080L),
            entry(NetworkAttributes.SERVER_SOCKET_ADDRESS, "127.0.0.1"),
            entry(NetworkAttributes.SERVER_SOCKET_PORT, 12345L),
            entry(NetworkAttributes.NETWORK_TYPE, "ipv4"),
            entry(NetworkAttributes.NETWORK_TRANSPORT, "tcp"));
  }
}
