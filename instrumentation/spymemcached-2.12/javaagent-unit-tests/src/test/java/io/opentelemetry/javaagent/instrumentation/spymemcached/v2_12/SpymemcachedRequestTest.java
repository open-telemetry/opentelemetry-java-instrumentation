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
import io.opentelemetry.context.Scope;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import net.spy.memcached.MemcachedConnection;
import net.spy.memcached.MemcachedNode;
import net.spy.memcached.ops.KeyedOperation;
import net.spy.memcached.ops.Operation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
  void bulkRetryPermanentlyOmitsHandlingNode() {
    MemcachedConnection connection = mock(MemcachedConnection.class);
    SpymemcachedRequest request = SpymemcachedRequest.create(connection, "asyncGetBulk");
    Operation operation = operation("one.example", 11211);
    Context context = SpymemcachedRequestHolder.init(Context.root(), request);
    SpymemcachedRequestHolder.trackOperation(context, operation);
    SpymemcachedRequestHolder.captureHandlingNode(context, operation, operation.getHandlingNode());
    assertThat(request.getHandlingNodeAddress()).isEqualTo(node("one.example", 11211));

    assertThat(SpymemcachedRequestHolder.startRetry(operation)).isNull();
    assertThat(request.getHandlingNodeAddress()).isNull();
    request.setHandlingNode(memcachedNode("two.example", 11212));

    assertThat(request.getHandlingNodeAddress()).isNull();
  }

  @Test
  void suppressionBeforeCaptureOmitsHandlingNode() {
    SpymemcachedRequest request =
        SpymemcachedRequest.create(mock(MemcachedConnection.class), "asyncGet");

    request.suppressHandlingNodeAddress();
    request.setHandlingNode(memcachedNode("one.example", 11211));

    assertThat(request.getHandlingNodeAddress()).isNull();
  }

  @Test
  void suppressionDuringCaptureOmitsHandlingNode() {
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
                          request.suppressHandlingNodeAddress();
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
    SpymemcachedRequestHolder.captureHandlingNode(
        firstContext, firstOperation, firstOperation.getHandlingNode());
    SpymemcachedRequestHolder.trackOperation(secondContext, secondOperation);
    SpymemcachedRequestHolder.captureHandlingNode(
        secondContext, secondOperation, secondOperation.getHandlingNode());
    Operation optimizedOperation = mock(Operation.class);
    SpymemcachedRequestHolder.propagateOperation(optimizedOperation, firstOperation);
    SpymemcachedRequestHolder.propagateOperation(optimizedOperation, secondOperation);

    assertThat(SpymemcachedRequestHolder.startRetry(optimizedOperation)).isNull();

    assertThat(firstRequest.getHandlingNodeAddress()).isNull();
    assertThat(secondRequest.getHandlingNodeAddress()).isNull();
  }

  @Test
  void sequentialSingleKeyRetriesUseLatestNode() {
    MemcachedConnection connection = mock(MemcachedConnection.class);
    SpymemcachedSingletons.setServerTarget(connection, singletonList(node("configured", 11211)));
    SpymemcachedRequest request = SpymemcachedRequest.create(connection, "asyncGet");
    Context context = SpymemcachedRequestHolder.init(Context.root(), request);
    Context original = Context.current();
    Operation initialOperation = operation("first", 11211);
    SpymemcachedRequestHolder.trackOperation(context, initialOperation);
    SpymemcachedRequestHolder.captureHandlingNode(
        context, initialOperation, initialOperation.getHandlingNode());

    Operation firstRetry = operation("second", 11212);
    try (Scope scope = SpymemcachedRequestHolder.startRetry(initialOperation)) {
      assertThat(scope).isNotNull();
      SpymemcachedRequestHolder.trackOperation(Context.current(), firstRetry);
      SpymemcachedRequestHolder.captureHandlingNode(
          Context.current(), firstRetry, firstRetry.getHandlingNode());
    }
    assertThat(request.getHandlingNodeAddress()).isEqualTo(node("second", 11212));

    Operation secondRetry = operation("third", 11213);
    try (Scope scope = SpymemcachedRequestHolder.startRetry(firstRetry)) {
      assertThat(scope).isNotNull();
      SpymemcachedRequestHolder.trackOperation(Context.current(), secondRetry);
      SpymemcachedRequestHolder.captureHandlingNode(
          Context.current(), secondRetry, secondRetry.getHandlingNode());
    }

    assertThat(request.getHandlingNodeAddress()).isEqualTo(node("third", 11213));
    assertThat(request.getServerTarget().getAddress()).isEqualTo("configured");
    assertThat(Context.current()).isSameAs(original);
  }

  @Test
  void optimizedRetryWithOneTrackedRequestStillOmitsHandlingNode() {
    SpymemcachedRequest request =
        SpymemcachedRequest.create(mock(MemcachedConnection.class), "asyncGet");
    Context context = SpymemcachedRequestHolder.init(Context.root(), request);
    Operation operation = operation("first", 11211);
    SpymemcachedRequestHolder.trackOperation(context, operation);
    SpymemcachedRequestHolder.captureHandlingNode(context, operation, operation.getHandlingNode());
    Operation optimizedOperation = operation("first", 11211);
    SpymemcachedRequestHolder.propagateOperation(optimizedOperation, operation);

    assertThat(SpymemcachedRequestHolder.startRetry(optimizedOperation)).isNull();
    assertThat(request.getHandlingNodeAddress()).isNull();
  }

  @Test
  void multiKeyRetryChildSuppressesHandlingNode() {
    SpymemcachedRequest request =
        SpymemcachedRequest.create(mock(MemcachedConnection.class), "asyncGet");
    Context context = SpymemcachedRequestHolder.init(Context.root(), request);
    Operation initialOperation = operation("first", 11211);
    SpymemcachedRequestHolder.trackOperation(context, initialOperation);
    SpymemcachedRequestHolder.captureHandlingNode(
        context, initialOperation, initialOperation.getHandlingNode());
    KeyedOperation retryOperation = mock(KeyedOperation.class);
    when(retryOperation.getKeys()).thenReturn(asList("one", "two"));

    try (Scope scope = SpymemcachedRequestHolder.startRetry(initialOperation)) {
      assertThat(scope).isNotNull();
      SpymemcachedRequestHolder.trackOperation(Context.current(), retryOperation);
      SpymemcachedRequestHolder.captureHandlingNode(
          Context.current(), retryOperation, memcachedNode("second", 11212));
    }

    assertThat(request.getHandlingNodeAddress()).isNull();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void cancelledOrTimedOutOperationDoesNotInvalidatePeer(boolean timedOut) {
    SpymemcachedRequest request =
        SpymemcachedRequest.create(mock(MemcachedConnection.class), "asyncGetBulk");
    Context context = SpymemcachedRequestHolder.init(Context.root(), request);
    Operation operation = operation("first", 11211);
    SpymemcachedRequestHolder.trackOperation(context, operation);
    SpymemcachedRequestHolder.captureHandlingNode(context, operation, operation.getHandlingNode());
    when(operation.isCancelled()).thenReturn(!timedOut);
    when(operation.isTimedOut()).thenReturn(timedOut);

    assertThat(SpymemcachedRequestHolder.startRetry(operation)).isNull();
    assertThat(request.getHandlingNodeAddress()).isEqualTo(node("first", 11211));
  }

  @Test
  void retryWithUnsupportedAddressClearsPreviousAddress() {
    SpymemcachedRequest request =
        SpymemcachedRequest.create(mock(MemcachedConnection.class), "asyncGet");
    request.setHandlingNode(memcachedNode("first", 11211));
    MemcachedNode unsupportedNode = mock(MemcachedNode.class);
    when(unsupportedNode.getSocketAddress()).thenReturn(mock(SocketAddress.class));

    request.setRetryHandlingNode(unsupportedNode);

    assertThat(request.getHandlingNodeAddress()).isNull();
  }

  @Test
  void unrelatedOperationCannotUpdateHandlingNode() {
    SpymemcachedRequest request =
        SpymemcachedRequest.create(mock(MemcachedConnection.class), "asyncGet");
    Context context = SpymemcachedRequestHolder.init(Context.root(), request);

    SpymemcachedRequestHolder.captureHandlingNode(
        context, operation("first", 11211), memcachedNode("first", 11211));

    assertThat(request.getHandlingNodeAddress()).isNull();
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
    KeyedOperation operation = mock(KeyedOperation.class);
    when(operation.getKeys()).thenReturn(singletonList("key"));
    MemcachedNode node = memcachedNode(host, port);
    when(operation.getHandlingNode()).thenReturn(node);
    return operation;
  }

  private static InetSocketAddress node(String host, int port) {
    return InetSocketAddress.createUnresolved(host, port);
  }
}
