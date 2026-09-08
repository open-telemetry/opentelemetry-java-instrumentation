/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spymemcached.v2_12;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.opentelemetry.context.Context;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import net.spy.memcached.MemcachedConnection;
import net.spy.memcached.MemcachedNode;
import net.spy.memcached.ops.Operation;
import org.junit.jupiter.api.Test;

class SpymemcachedRequestTest {

  @Test
  void requestCarriesTheTargetItsConnectionWasCreatedFor() {
    MemcachedConnection connection = mock(MemcachedConnection.class);
    SpymemcachedSingletons.setServerTarget(
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
  void handlingNodeIsKeptBesideTheConfiguredTarget() {
    MemcachedConnection connection = mock(MemcachedConnection.class);
    SpymemcachedSingletons.setServerTarget(
        connection, asList(node("one.example", 11211), node("two.example", 11212)));
    SpymemcachedRequest request = SpymemcachedRequest.create(connection, "asyncGet");

    request.setHandlingNode(memcachedNode("two.example", 11212));

    assertThat(request.getHandlingNodeAddress()).isEqualTo(node("two.example", 11212));
    assertThat(request.getServerTarget().getAddress())
        .isEqualTo("one.example:11211,two.example:11212");
  }

  @Test
  void nonInetHandlingNodeIsIgnored() {
    MemcachedConnection connection = mock(MemcachedConnection.class);
    SpymemcachedRequest request = SpymemcachedRequest.create(connection, "asyncGet");
    MemcachedNode node = mock(MemcachedNode.class);
    when(node.getSocketAddress()).thenReturn(mock(SocketAddress.class));

    request.setHandlingNode(node);

    assertThat(request.getHandlingNodeAddress()).isNull();
  }

  @Test
  void initialOperationsOnSeveralNodesHaveNoHandlingNode() {
    MemcachedConnection connection = mock(MemcachedConnection.class);
    SpymemcachedRequest request = SpymemcachedRequest.create(connection, "asyncGetBulk");

    request.setHandlingNode(memcachedNode("one.example", 11211));
    request.setHandlingNode(memcachedNode("two.example", 11212));

    assertThat(request.getHandlingNodeAddress()).isNull();

    request.setHandlingNode(memcachedNode("one.example", 11211));

    assertThat(request.getHandlingNodeAddress()).isNull();
  }

  @Test
  void unsupportedNodeStillMakesBulkHandlingAmbiguous() {
    MemcachedConnection connection = mock(MemcachedConnection.class);
    SpymemcachedRequest request = SpymemcachedRequest.create(connection, "asyncGetBulk");
    MemcachedNode unsupportedNode = mock(MemcachedNode.class);
    when(unsupportedNode.getSocketAddress()).thenReturn(mock(SocketAddress.class));

    request.setHandlingNode(unsupportedNode);
    request.setHandlingNode(memcachedNode("two.example", 11212));

    assertThat(request.getHandlingNodeAddress()).isNull();
  }

  @Test
  void redistributionPermanentlyOmitsHandlingNode() {
    MemcachedConnection connection = mock(MemcachedConnection.class);
    SpymemcachedRequest request = SpymemcachedRequest.create(connection, "asyncGet");
    Operation operation = operation("one.example", 11211);
    Context context = SpymemcachedRequestHolder.init(Context.root(), request);
    SpymemcachedRequestHolder.trackOperation(context, operation);
    SpymemcachedRequestHolder.captureHandlingNode(context, operation);
    assertThat(request.getHandlingNodeAddress()).isEqualTo(node("one.example", 11211));

    SpymemcachedRequestHolder.markRedistributed(operation);
    assertThat(request.getHandlingNodeAddress()).isNull();
    request.setHandlingNode(memcachedNode("two.example", 11212));

    assertThat(request.getHandlingNodeAddress()).isNull();
  }

  @Test
  void redistributionBeforeCaptureOmitsHandlingNode() {
    SpymemcachedRequest request =
        SpymemcachedRequest.create(mock(MemcachedConnection.class), "asyncGet");

    request.markRedistributed();
    request.setHandlingNode(memcachedNode("one.example", 11211));

    assertThat(request.getHandlingNodeAddress()).isNull();
  }

  @Test
  void redistributionDuringCaptureOmitsHandlingNode() {
    SpymemcachedRequest request =
        SpymemcachedRequest.create(mock(MemcachedConnection.class), "asyncGet");
    MemcachedNode node = mock(MemcachedNode.class);
    InetSocketAddress address = node("one.example", 11211);
    ExecutorService ioThread = Executors.newSingleThreadExecutor();
    try {
      when(node.getSocketAddress())
          .thenAnswer(
              invocation -> {
                ioThread
                    .submit(
                        () -> {
                          request.markRedistributed();
                          assertThat(request.getHandlingNodeAddress()).isNull();
                        })
                    .get(10, SECONDS);
                return address;
              });

      request.setHandlingNode(node);
    } finally {
      ioThread.shutdownNow();
    }

    assertThat(request.getHandlingNodeAddress()).isNull();
    request.setHandlingNode(memcachedNode("two.example", 11212));
    assertThat(request.getHandlingNodeAddress()).isNull();
  }

  @Test
  void optimizedRedistributionOmitsHandlingNodeFromEveryTrackedRequest() {
    MemcachedConnection connection = mock(MemcachedConnection.class);
    SpymemcachedRequest firstRequest = SpymemcachedRequest.create(connection, "asyncGet");
    SpymemcachedRequest secondRequest = SpymemcachedRequest.create(connection, "asyncGet");
    Operation firstOperation = operation("one.example", 11211);
    Operation secondOperation = operation("one.example", 11211);
    Context firstContext = SpymemcachedRequestHolder.init(Context.root(), firstRequest);
    Context secondContext = SpymemcachedRequestHolder.init(Context.root(), secondRequest);
    SpymemcachedRequestHolder.trackOperation(firstContext, firstOperation);
    SpymemcachedRequestHolder.captureHandlingNode(firstContext, firstOperation);
    SpymemcachedRequestHolder.trackOperation(secondContext, secondOperation);
    SpymemcachedRequestHolder.captureHandlingNode(secondContext, secondOperation);
    Operation optimizedOperation = mock(Operation.class);
    SpymemcachedRequestHolder.propagateOperation(optimizedOperation, firstOperation);
    SpymemcachedRequestHolder.propagateOperation(optimizedOperation, secondOperation);

    SpymemcachedRequestHolder.markRedistributed(optimizedOperation);

    assertThat(firstRequest.getHandlingNodeAddress()).isNull();
    assertThat(secondRequest.getHandlingNodeAddress()).isNull();
  }

  @Test
  void handlingNodeIsHeldPerRequest() {
    MemcachedConnection connection = mock(MemcachedConnection.class);
    SpymemcachedSingletons.setServerTarget(connection, singletonList(node("one.example", 11211)));
    SpymemcachedRequest first = SpymemcachedRequest.create(connection, "asyncGet");
    SpymemcachedRequest second = SpymemcachedRequest.create(connection, "asyncGet");

    first.setHandlingNode(memcachedNode("one.example", 11211));

    assertThat(second.getHandlingNodeAddress()).isNull();
    assertThat(first.getHandlingNodeAddress()).isEqualTo(node("one.example", 11211));
  }

  @Test
  void handlingNodeAddressIsCapturedWhenOperationIsEnqueued() {
    MemcachedConnection connection = mock(MemcachedConnection.class);
    SpymemcachedRequest request = SpymemcachedRequest.create(connection, "asyncGet");
    InetSocketAddress first = node("one.example", 11211);
    AtomicReference<InetSocketAddress> address = new AtomicReference<>(first);
    MemcachedNode node = mock(MemcachedNode.class);
    when(node.getSocketAddress()).thenAnswer(invocation -> address.get());

    request.setHandlingNode(node);
    address.set(node("two.example", 11212));

    assertThat(request.getHandlingNodeAddress()).isEqualTo(first);
    verify(node).getSocketAddress();
  }

  private static MemcachedNode memcachedNode(String host, int port) {
    MemcachedNode node = mock(MemcachedNode.class);
    when(node.getSocketAddress()).thenReturn(node(host, port));
    return node;
  }

  private static Operation operation(String host, int port) {
    Operation operation = mock(Operation.class);
    MemcachedNode node = memcachedNode(host, port);
    when(operation.getHandlingNode()).thenReturn(node);
    return operation;
  }

  private static InetSocketAddress node(String host, int port) {
    return InetSocketAddress.createUnresolved(host, port);
  }
}
