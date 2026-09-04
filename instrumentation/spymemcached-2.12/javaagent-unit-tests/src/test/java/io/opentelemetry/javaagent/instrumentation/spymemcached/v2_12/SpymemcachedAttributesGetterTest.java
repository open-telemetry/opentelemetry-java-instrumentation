/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spymemcached.v2_12;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.semconv.network.ServerAttributesExtractor;
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

  @Test
  void selectedNodeIsReportedOnlyWhenStableTelemetryIsDisabled() {
    MemcachedConnection connection = mock(MemcachedConnection.class);
    SpymemcachedRequest request = SpymemcachedRequest.create(connection, "asyncGet");
    request.setHandlingNode(memcachedNode("selected.example", 11212));
    AttributesBuilder attributes = Attributes.builder();

    extractServerAttributes(attributes, request);

    Attributes result = attributes.build();
    assertThat(result.get(SERVER_ADDRESS))
        .isEqualTo(emitStableDatabaseSemconv() ? null : "selected.example");
    assertThat(result.get(SERVER_PORT)).isEqualTo(emitStableDatabaseSemconv() ? null : 11212L);
  }

  @Test
  void selectedNodeDoesNotOverwriteStableConfiguredTarget() {
    MemcachedConnection connection = mock(MemcachedConnection.class);
    SpymemcachedSingletons.setServerTarget(
        connection, asList(node("one.example", 11212), node("two.example", 11212)));
    SpymemcachedRequest request = SpymemcachedRequest.create(connection, "asyncGet");
    request.setHandlingNode(memcachedNode("selected.example", 11213));
    AttributesBuilder attributes = Attributes.builder();
    attributes.put(SERVER_ADDRESS, getter.getServerAddress(request));
    Integer serverPort = getter.getServerPort(request);
    if (serverPort != null) {
      attributes.put(SERVER_PORT, serverPort);
    }

    extractServerAttributes(attributes, request);

    Attributes result = attributes.build();
    assertThat(result.get(SERVER_ADDRESS))
        .isEqualTo(
            emitStableDatabaseSemconv()
                ? "one.example:11212,two.example:11212"
                : "selected.example");
    assertThat(result.get(SERVER_PORT)).isEqualTo(emitStableDatabaseSemconv() ? null : 11213L);
    assertThat(request.getServerTarget().getAddress())
        .isEqualTo("one.example:11212,two.example:11212");
  }

  @Test
  void resolvedHandlingNodeIsTheStableNetworkPeer() throws UnknownHostException {
    SpymemcachedRequest request = request(singletonList(node("one.example", 11211)));
    InetSocketAddress peer =
        new InetSocketAddress(InetAddress.getByAddress(new byte[] {10, 20, 30, 40}), 11211);
    request.setHandlingNode(memcachedNode(peer));

    assertThat(getter.getNetworkPeerInetSocketAddress(request, null))
        .isEqualTo(emitStableDatabaseSemconv() ? peer : null);
    assertThat(getter.getNetworkPeerAddress(request, null))
        .isEqualTo(emitStableDatabaseSemconv() ? "10.20.30.40" : null);
    assertThat(getter.getNetworkPeerPort(request, null))
        .isEqualTo(emitStableDatabaseSemconv() ? 11211 : null);
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
  void severalHandlingNodesHaveNoNetworkPeer() throws UnknownHostException {
    SpymemcachedRequest request =
        request(asList(node("one.example", 11211), node("two.example", 11212)));
    InetSocketAddress firstPeer =
        new InetSocketAddress(InetAddress.getByAddress(new byte[] {10, 20, 30, 40}), 11211);
    InetSocketAddress secondPeer =
        new InetSocketAddress(InetAddress.getByAddress(new byte[] {10, 20, 30, 41}), 11212);
    request.setHandlingNode(memcachedNode(firstPeer));
    request.setHandlingNode(memcachedNode(secondPeer));

    assertThat(getter.getNetworkPeerInetSocketAddress(request, null)).isNull();
  }

  private static SpymemcachedRequest request(List<InetSocketAddress> nodes) {
    MemcachedConnection connection = mock(MemcachedConnection.class);
    SpymemcachedSingletons.setServerTarget(connection, nodes);
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

  private static void extractServerAttributes(
      AttributesBuilder attributes, SpymemcachedRequest request) {
    if (!emitStableDatabaseSemconv()) {
      ServerAttributesExtractor.create(new SpymemcachedAttributesGetter())
          .onStart(attributes, Context.root(), request);
    }
  }

  private static InetSocketAddress node(String host, int port) {
    return InetSocketAddress.createUnresolved(host, port);
  }
}
