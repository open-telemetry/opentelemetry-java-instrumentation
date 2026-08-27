/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spymemcached.v2_12;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.List;
import net.spy.memcached.MemcachedConnection;
import net.spy.memcached.MemcachedNode;
import org.junit.jupiter.api.Test;

class SpymemcachedAttributesGetterTest {

  private final SpymemcachedAttributesGetter getter = new SpymemcachedAttributesGetter();

  @Test
  void singleDefaultPortIsOmittedInStableTelemetry() {
    SpymemcachedRequest request = request(singletonList(node("one.example", 11211)));
    request.setHandlingNode(memcachedNode("selected.example", 11212));

    assertThat(getter.getServerAddress(request))
        .isEqualTo(emitStableDatabaseSemconv() ? "one.example" : null);
    assertThat(getter.getServerPort(request)).isNull();
  }

  @Test
  void customPortsStayInlineInStableMultiEndpointTelemetry() {
    SpymemcachedRequest request =
        request(asList(node("one.example", 11212), node("two.example", 11212)));
    request.setHandlingNode(memcachedNode("two.example", 11212));

    assertThat(getter.getServerAddress(request))
        .isEqualTo(emitStableDatabaseSemconv() ? "one.example:11212,two.example:11212" : null);
    assertThat(getter.getServerPort(request)).isNull();
  }

  @Test
  void mixedPortsStayInlineInStableTelemetry() {
    SpymemcachedRequest request =
        request(asList(node("one.example", 11211), node("two.example", 11212)));
    request.setHandlingNode(memcachedNode("selected.example", 11213));

    assertThat(getter.getServerAddress(request))
        .isEqualTo(emitStableDatabaseSemconv() ? "one.example:11211,two.example:11212" : null);
    assertThat(getter.getServerPort(request)).isNull();
  }

  @Test
  void clientWithoutAConfiguredTargetNamesNoServer() {
    SpymemcachedRequest request =
        SpymemcachedRequest.create(mock(MemcachedConnection.class), "asyncGet");
    request.setHandlingNode(memcachedNode("one.example", 11211));

    assertThat(getter.getServerAddress(request)).isNull();
    assertThat(getter.getServerPort(request)).isNull();
  }

  @Test
  void resolvedHandlingNodeIsTheNetworkPeer() throws UnknownHostException {
    SpymemcachedRequest request = request(singletonList(node("one.example", 11211)));
    InetSocketAddress peer =
        new InetSocketAddress(InetAddress.getByAddress(new byte[] {10, 20, 30, 40}), 11211);
    request.setHandlingNode(memcachedNode(peer));

    assertThat(getter.getNetworkPeerInetSocketAddress(request, null)).isSameAs(peer);
  }

  @Test
  void unresolvedHandlingNodeIsNotResolved() {
    SpymemcachedRequest request = request(singletonList(node("one.example", 11211)));
    InetSocketAddress unresolved = node("unresolved.example", 11211);
    request.setHandlingNode(memcachedNode(unresolved));

    assertThat(getter.getNetworkPeerInetSocketAddress(request, null)).isNull();
    assertThat(getter.getNetworkPeerAddress(request, null)).isNull();
    assertThat(getter.getNetworkPeerPort(request, null)).isNull();
  }

  @Test
  void severalHandlingNodesHaveNoNetworkPeer() {
    SpymemcachedRequest request =
        request(asList(node("one.example", 11211), node("two.example", 11212)));
    request.setHandlingNode(memcachedNode("one.example", 11211));
    request.setHandlingNode(memcachedNode("two.example", 11212));

    assertThat(getter.getNetworkPeerInetSocketAddress(request, null)).isNull();
  }

  private static SpymemcachedRequest request(List<InetSocketAddress> nodes) {
    MemcachedConnection connection = mock(MemcachedConnection.class);
    SpymemcachedServerTargets.capture(connection, nodes);
    return SpymemcachedRequest.create(connection, "asyncGet");
  }

  private static MemcachedNode memcachedNode(String host, int port) {
    return memcachedNode(node(host, port));
  }

  private static MemcachedNode memcachedNode(InetSocketAddress address) {
    MemcachedNode node = mock(MemcachedNode.class);
    when(node.getSocketAddress()).thenReturn(address);
    return node;
  }

  private static InetSocketAddress node(String host, int port) {
    return InetSocketAddress.createUnresolved(host, port);
  }
}
