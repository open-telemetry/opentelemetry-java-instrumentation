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
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import net.spy.memcached.MemcachedConnection;
import net.spy.memcached.MemcachedNode;
import org.junit.jupiter.api.Test;

class SpymemcachedRequestTest {

  @Test
  void requestCarriesTheTargetItsConnectionWasCreatedFor() {
    MemcachedConnection connection = mock(MemcachedConnection.class);
    SpymemcachedServerTargets.capture(
        connection, asList(node("one.example", 11211), node("two.example", 11212)));

    SpymemcachedRequest request = SpymemcachedRequest.create(connection, "asyncGet");

    assertThat(request.getServerTarget().getAddress())
        .isEqualTo("one.example:11211,two.example:11212");
    assertThat(request.getServerTarget().getPort()).isNull();
  }

  @Test
  void connectionTheInstrumentationDidNotSeeBeingCreatedHasNoTarget() {
    MemcachedConnection connection = mock(MemcachedConnection.class);

    assertThat(SpymemcachedRequest.create(connection, "asyncGet").getServerTarget()).isNull();
  }

  @Test
  void selectedNodeIsReportedOnlyWhenStableTelemetryIsDisabled() {
    MemcachedConnection connection = mock(MemcachedConnection.class);
    SpymemcachedRequest request = SpymemcachedRequest.create(connection, "asyncGet");
    request.setHandlingNode(memcachedNode("selected.example", 11212));
    AttributesBuilder attributes = Attributes.builder();

    new SpymemcachedServerAttributesExtractor()
        .onEnd(attributes, Context.root(), request, null, null);

    Attributes result = attributes.build();
    assertThat(result.get(SERVER_ADDRESS))
        .isEqualTo(emitStableDatabaseSemconv() ? null : "selected.example");
    assertThat(result.get(SERVER_PORT)).isEqualTo(emitStableDatabaseSemconv() ? null : 11212L);
  }

  @Test
  void selectedNodeDoesNotOverwriteStableConfiguredTarget() {
    MemcachedConnection connection = mock(MemcachedConnection.class);
    SpymemcachedServerTargets.capture(
        connection, asList(node("one.example", 11212), node("two.example", 11212)));
    SpymemcachedRequest request = SpymemcachedRequest.create(connection, "asyncGet");
    request.setHandlingNode(memcachedNode("selected.example", 11213));
    AttributesBuilder attributes = Attributes.builder();
    SpymemcachedAttributesGetter getter = new SpymemcachedAttributesGetter();
    attributes.put(SERVER_ADDRESS, getter.getServerAddress(request));
    Integer serverPort = getter.getServerPort(request);
    if (serverPort != null) {
      attributes.put(SERVER_PORT, serverPort);
    }

    new SpymemcachedServerAttributesExtractor()
        .onEnd(attributes, Context.root(), request, null, null);

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
  void targetIsNotChangedByLaterEditsToTheConfiguredNodeList() {
    List<InetSocketAddress> nodes = new ArrayList<>();
    nodes.add(node("one.example", 11211));

    MemcachedConnection connection = mock(MemcachedConnection.class);
    SpymemcachedServerTargets.capture(connection, nodes);

    nodes.add(node("two.example", 11212));

    assertThat(SpymemcachedRequest.create(connection, "asyncGet").getServerTarget().getAddress())
        .isEqualTo("one.example");
  }

  @Test
  void handlingNodeIsKeptBesideTheConfiguredTarget() {
    MemcachedConnection connection = mock(MemcachedConnection.class);
    SpymemcachedServerTargets.capture(
        connection, asList(node("one.example", 11211), node("two.example", 11212)));
    SpymemcachedRequest request = SpymemcachedRequest.create(connection, "asyncGet");

    request.setHandlingNode(memcachedNode("two.example", 11212));

    assertThat(request.getHandlingNodeAddress()).isEqualTo(node("two.example", 11212));
    assertThat(request.getServerTarget().getAddress())
        .isEqualTo("one.example:11211,two.example:11212");
  }

  @Test
  void sequentialSingleKeyRetryDoesNotChangeTheConfiguredTarget() {
    MemcachedConnection connection = mock(MemcachedConnection.class);
    SpymemcachedServerTargets.capture(
        connection, asList(node("one.example", 11211), node("two.example", 11212)));
    SpymemcachedRequest request = SpymemcachedRequest.create(connection, "asyncGet");

    request.setHandlingNode(memcachedNode("one.example", 11211));
    request.setHandlingNode(memcachedNode("two.example", 11212));
    assertThat(request.getHandlingNodeAddress()).isNull();
    assertThat(request.getServerTarget().getAddress())
        .isEqualTo("one.example:11211,two.example:11212");
  }

  @Test
  void handlingNodeIsHeldPerRequest() {
    MemcachedConnection connection = mock(MemcachedConnection.class);
    SpymemcachedServerTargets.capture(connection, singletonList(node("one.example", 11211)));

    SpymemcachedRequest first = SpymemcachedRequest.create(connection, "asyncGet");
    SpymemcachedRequest second = SpymemcachedRequest.create(connection, "asyncGet");
    first.setHandlingNode(memcachedNode("one.example", 11211));

    assertThat(second.getHandlingNodeAddress()).isNull();
    assertThat(first.getHandlingNodeAddress()).isEqualTo(node("one.example", 11211));
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
