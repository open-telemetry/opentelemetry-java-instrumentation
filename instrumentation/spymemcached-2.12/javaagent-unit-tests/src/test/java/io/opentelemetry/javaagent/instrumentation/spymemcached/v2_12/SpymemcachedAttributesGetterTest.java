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

class SpymemcachedAttributesGetterTest {

  private final SpymemcachedAttributesGetter getter = new SpymemcachedAttributesGetter();

  @Test
  void singleDefaultPortIsOmittedInStableTelemetry() {
    SpymemcachedRequest request = request(singletonList(node("one.example", 11211)));
    request.setHandlingNode(memcachedNode("selected.example", 11212));

    assertThat(getter.getServerAddress(request))
        .isEqualTo(emitStableDatabaseSemconv() ? "one.example" : "selected.example");
    assertThat(getter.getServerPort(request)).isEqualTo(emitStableDatabaseSemconv() ? null : 11212);
  }

  @Test
  void customPortsStayInlineInStableMultiEndpointTelemetry() {
    SpymemcachedRequest request =
        request(asList(node("one.example", 11212), node("two.example", 11212)));
    request.setHandlingNode(memcachedNode("two.example", 11212));

    assertThat(getter.getServerAddress(request))
        .isEqualTo(
            emitStableDatabaseSemconv() ? "one.example:11212,two.example:11212" : "two.example");
    assertThat(getter.getServerPort(request)).isEqualTo(emitStableDatabaseSemconv() ? null : 11212);
  }

  @Test
  void mixedPortsStayInlineInStableTelemetry() {
    SpymemcachedRequest request =
        request(asList(node("one.example", 11211), node("two.example", 11212)));
    request.setHandlingNode(memcachedNode("selected.example", 11213));

    assertThat(getter.getServerAddress(request))
        .isEqualTo(
            emitStableDatabaseSemconv()
                ? "one.example:11211,two.example:11212"
                : "selected.example");
    assertThat(getter.getServerPort(request)).isEqualTo(emitStableDatabaseSemconv() ? null : 11213);
  }

  @Test
  void clientWithoutAConfiguredTargetUsesHandlingNodeInLegacyTelemetry() {
    SpymemcachedRequest request =
        SpymemcachedRequest.create(mock(MemcachedConnection.class), "asyncGet");
    request.setHandlingNode(memcachedNode("one.example", 11211));

    assertThat(getter.getServerAddress(request))
        .isEqualTo(emitStableDatabaseSemconv() ? null : "one.example");
    assertThat(getter.getServerPort(request)).isEqualTo(emitStableDatabaseSemconv() ? null : 11211);
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
