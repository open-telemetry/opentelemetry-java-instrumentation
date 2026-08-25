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

import java.net.InetSocketAddress;
import java.util.List;
import net.spy.memcached.MemcachedConnection;
import net.spy.memcached.MemcachedNode;
import org.junit.jupiter.api.Test;

/**
 * Runs in every semantic convention mode the module supports, so that each assertion below reads as
 * what that mode describes. Stable and dual report a single configured target; the old conventions
 * describe the node that answered instead, which is only known once the request has ended.
 */
class SpymemcachedAttributesGetterTest {

  private final SpymemcachedAttributesGetter getter = new SpymemcachedAttributesGetter();

  @Test
  void singleConfiguredNodeKeepsItsAddressAndPort() {
    SpymemcachedRequest request = request(singletonList(node("one.example", 11211)));
    request.setHandlingNode(memcachedNode("one.example", 11211));

    assertThat(getter.getServerAddress(request))
        .isEqualTo(emitStableDatabaseSemconv() ? "one.example" : null);
    assertThat(getter.getServerPort(request)).isEqualTo(emitStableDatabaseSemconv() ? 11211 : null);
  }

  @Test
  void severalConfiguredNodesHaveNoSingleServerAddress() {
    SpymemcachedRequest request =
        request(asList(node("one.example", 11211), node("two.example", 11212)));
    request.setHandlingNode(memcachedNode("two.example", 11212));

    assertThat(getter.getServerAddress(request)).isNull();
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

  private static SpymemcachedRequest request(List<InetSocketAddress> nodes) {
    MemcachedConnection connection = mock(MemcachedConnection.class);
    SpymemcachedServerTargets.capture(connection, nodes);
    return SpymemcachedRequest.create(connection, "asyncGet");
  }

  private static MemcachedNode memcachedNode(String host, int port) {
    MemcachedNode node = mock(MemcachedNode.class);
    when(node.getSocketAddress()).thenReturn(node(host, port));
    return node;
  }

  private static InetSocketAddress node(String host, int port) {
    return InetSocketAddress.createUnresolved(host, port);
  }
}
