/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.grpc.MethodDescriptor;
import io.netty.channel.unix.DomainSocketAddress;
import io.opentelemetry.instrumentation.api.semconv.network.internal.UnixSocketAddress;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import org.junit.jupiter.api.Test;

class GrpcRequestSocketAddressTest {

  @Test
  void serverRequestReusesNormalizedEndpoints() {
    SocketAddress local = new DomainSocketAddress("local.sock");
    SocketAddress peer =
        new io.grpc.netty.shaded.io.netty.channel.unix.DomainSocketAddress("peer.sock");
    GrpcRequest request =
        GrpcRequest.createServerRequest(mock(MethodDescriptor.class), null, peer, local, null);
    GrpcNetworkAttributesGetter getter = new GrpcNetworkAttributesGetter();

    SocketAddress normalizedLocal = getter.getNetworkLocalSocketAddress(request, null);
    SocketAddress normalizedPeer = getter.getNetworkPeerSocketAddress(request, null);
    assertThat(normalizedLocal)
        .isInstanceOfSatisfying(
            UnixSocketAddress.class,
            address -> assertThat(address.getPath()).isEqualTo("local.sock"));
    assertThat(normalizedPeer)
        .isInstanceOfSatisfying(
            UnixSocketAddress.class,
            address -> assertThat(address.getPath()).isEqualTo("peer.sock"));
    assertThat(getter.getNetworkLocalSocketAddress(request, null)).isSameAs(normalizedLocal);
    assertThat(getter.getNetworkPeerSocketAddress(request, null)).isSameAs(normalizedPeer);
    assertThat(request.getLocalSocketAddress()).isSameAs(local);
    assertThat(request.getPeerSocketAddress()).isSameAs(peer);
  }

  @Test
  void clientRequestUsesUpdatedEndpoints() {
    GrpcRequest request = GrpcRequest.createClientRequest(mock(MethodDescriptor.class), null, null);
    GrpcNetworkAttributesGetter getter = new GrpcNetworkAttributesGetter();

    request.setLocalSocketAddress(new DomainSocketAddress("local.sock"));
    request.setPeerSocketAddress(new DomainSocketAddress("peer.sock"));
    SocketAddress normalizedLocal = getter.getNetworkLocalSocketAddress(request, null);
    SocketAddress normalizedPeer = getter.getNetworkPeerSocketAddress(request, null);
    assertThat(normalizedLocal).isInstanceOf(UnixSocketAddress.class);
    assertThat(normalizedPeer).isInstanceOf(UnixSocketAddress.class);
    assertThat(getter.getNetworkLocalSocketAddress(request, null)).isSameAs(normalizedLocal);
    assertThat(getter.getNetworkPeerSocketAddress(request, null)).isSameAs(normalizedPeer);

    SocketAddress local = new InetSocketAddress(InetAddress.getLoopbackAddress(), 8080);
    request.setLocalSocketAddress(local);
    request.setPeerSocketAddress(null);
    assertThat(getter.getNetworkLocalSocketAddress(request, null)).isSameAs(local);
    assertThat(getter.getNetworkPeerSocketAddress(request, null)).isNull();
    assertThat(request.getLocalSocketAddress()).isSameAs(local);
    assertThat(request.getPeerSocketAddress()).isNull();
  }
}
