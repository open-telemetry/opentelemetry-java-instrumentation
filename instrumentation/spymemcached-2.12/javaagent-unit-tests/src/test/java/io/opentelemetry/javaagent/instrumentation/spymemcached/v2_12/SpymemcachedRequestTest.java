/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spymemcached.v2_12;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.opentelemetry.context.Context;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import net.spy.memcached.MemcachedConnection;
import net.spy.memcached.MemcachedNode;
import net.spy.memcached.ops.KeyedOperation;
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

    request.captureHandlingNodeAddress();
    assertThat(request.getHandlingNodeAddress()).isEqualTo(node("two.example", 11212));
    assertThat(request.getServerTarget().getAddress())
        .isEqualTo("one.example:11211,two.example:11212");
  }

  @Test
  void initialOperationsOnSeveralNodesHaveNoHandlingNode() {
    MemcachedConnection connection = mock(MemcachedConnection.class);
    SpymemcachedSingletons.setServerTarget(
        connection, asList(node("one.example", 11211), node("two.example", 11212)));
    SpymemcachedRequest request = SpymemcachedRequest.create(connection, "asyncGet");

    request.setHandlingNode(memcachedNode("one.example", 11211));
    request.setHandlingNode(memcachedNode("two.example", 11212));
    request.captureHandlingNodeAddress();
    assertThat(request.getHandlingNodeAddress()).isNull();
    assertThat(request.getServerTarget().getAddress())
        .isEqualTo("one.example:11211,two.example:11212");
  }

  @Test
  void retryUsesTheNodeAssignedOnTheConnectionThread() {
    MemcachedConnection connection = mock(MemcachedConnection.class);
    SpymemcachedSingletons.setServerTarget(
        connection, asList(node("one.example", 11211), node("two.example", 11212)));
    SpymemcachedRequest request = SpymemcachedRequest.create(connection, "asyncGet");
    Operation initialOperation = operation("one.example", 11211);
    Context context = SpymemcachedRequestHolder.init(Context.root(), request);
    SpymemcachedRequestHolder.associateOperation(context, initialOperation);
    SpymemcachedRequestHolder.captureHandlingNode(context, initialOperation);

    try (SpymemcachedRequestHolder.RetryScope retryScope =
        SpymemcachedRequestHolder.startRetry(initialOperation)) {
      assertThat(retryScope).isNotNull();
      Operation retryOperation = operation("two.example", 11212);
      SpymemcachedRequestHolder.associateOperation(Context.current(), retryOperation);
      SpymemcachedRequestHolder.captureHandlingNode(Context.current(), retryOperation);
    }

    request.captureHandlingNodeAddress();
    assertThat(request.getHandlingNodeAddress()).isEqualTo(node("two.example", 11212));
    assertThat(request.getServerTarget().getAddress())
        .isEqualTo("one.example:11211,two.example:11212");
  }

  @Test
  void partialBulkRetryUsesRetryNode() {
    MemcachedConnection connection = mock(MemcachedConnection.class);
    SpymemcachedRequest request = SpymemcachedRequest.create(connection, "asyncGetBulk");
    Operation initialOperation = operation("one.example", 11211, "one", "two");
    Context context = SpymemcachedRequestHolder.init(Context.root(), request);
    SpymemcachedRequestHolder.associateOperation(context, initialOperation);
    SpymemcachedRequestHolder.captureHandlingNode(context, initialOperation);

    try (SpymemcachedRequestHolder.RetryScope retryScope =
        SpymemcachedRequestHolder.startRetry(initialOperation)) {
      assertThat(retryScope).isNotNull();
      Operation retryOperation = operation("two.example", 11212, "two");
      SpymemcachedRequestHolder.associateOperation(Context.current(), retryOperation);
      SpymemcachedRequestHolder.captureHandlingNode(Context.current(), retryOperation);
    }

    request.captureHandlingNodeAddress();
    assertThat(request.getHandlingNodeAddress()).isEqualTo(node("two.example", 11212));
  }

  @Test
  void partialBulkRetryToSameNodeKeepsHandlingNode() {
    MemcachedConnection connection = mock(MemcachedConnection.class);
    SpymemcachedRequest request = SpymemcachedRequest.create(connection, "asyncGetBulk");
    MemcachedNode node = memcachedNode("one.example", 11211);
    Operation initialOperation = operation(node, "one", "two");
    Context context = SpymemcachedRequestHolder.init(Context.root(), request);
    SpymemcachedRequestHolder.associateOperation(context, initialOperation);
    SpymemcachedRequestHolder.captureHandlingNode(context, initialOperation);

    try (SpymemcachedRequestHolder.RetryScope retryScope =
        SpymemcachedRequestHolder.startRetry(initialOperation)) {
      assertThat(retryScope).isNotNull();
      Operation retryOperation = operation(node, "two");
      SpymemcachedRequestHolder.associateOperation(Context.current(), retryOperation);
      SpymemcachedRequestHolder.captureHandlingNode(Context.current(), retryOperation);
    }

    request.captureHandlingNodeAddress();
    assertThat(request.getHandlingNodeAddress()).isEqualTo(node("one.example", 11211));
  }

  @Test
  void fullBulkRetryToOneNodeUsesRetryNode() {
    MemcachedConnection connection = mock(MemcachedConnection.class);
    SpymemcachedRequest request = SpymemcachedRequest.create(connection, "asyncGetBulk");
    Operation initialOperation = operation("one.example", 11211, "one", "two");
    Context context = SpymemcachedRequestHolder.init(Context.root(), request);
    SpymemcachedRequestHolder.associateOperation(context, initialOperation);
    SpymemcachedRequestHolder.captureHandlingNode(context, initialOperation);

    MemcachedNode retryNode = memcachedNode("two.example", 11212);
    try (SpymemcachedRequestHolder.RetryScope retryScope =
        SpymemcachedRequestHolder.startRetry(initialOperation)) {
      assertThat(retryScope).isNotNull();
      Operation firstRetry = operation(retryNode, "one");
      SpymemcachedRequestHolder.associateOperation(Context.current(), firstRetry);
      SpymemcachedRequestHolder.captureHandlingNode(Context.current(), firstRetry);
      Operation secondRetry = operation(retryNode, "two");
      SpymemcachedRequestHolder.associateOperation(Context.current(), secondRetry);
      SpymemcachedRequestHolder.captureHandlingNode(Context.current(), secondRetry);
    }

    request.captureHandlingNodeAddress();
    assertThat(request.getHandlingNodeAddress()).isEqualTo(node("two.example", 11212));
  }

  @Test
  void multiKeyRetryOntoSeveralNodesHasNoHandlingNode() {
    MemcachedConnection connection = mock(MemcachedConnection.class);
    SpymemcachedRequest request = SpymemcachedRequest.create(connection, "asyncGetBulk");
    Operation initialOperation = operation("one.example", 11211, "one", "two");
    Context context = SpymemcachedRequestHolder.init(Context.root(), request);
    SpymemcachedRequestHolder.associateOperation(context, initialOperation);
    SpymemcachedRequestHolder.captureHandlingNode(context, initialOperation);

    try (SpymemcachedRequestHolder.RetryScope retryScope =
        SpymemcachedRequestHolder.startRetry(initialOperation)) {
      assertThat(retryScope).isNotNull();
      Operation firstRetry = operation("two.example", 11212, "one");
      SpymemcachedRequestHolder.associateOperation(Context.current(), firstRetry);
      SpymemcachedRequestHolder.captureHandlingNode(Context.current(), firstRetry);
      Operation secondRetry = operation("three.example", 11213, "two");
      SpymemcachedRequestHolder.associateOperation(Context.current(), secondRetry);
      SpymemcachedRequestHolder.captureHandlingNode(Context.current(), secondRetry);
    }

    request.captureHandlingNodeAddress();
    assertThat(request.getHandlingNodeAddress()).isNull();
  }

  @Test
  void sequentialSingleKeyRetriesUseLastHandlingNode() {
    MemcachedConnection connection = mock(MemcachedConnection.class);
    SpymemcachedRequest request = SpymemcachedRequest.create(connection, "asyncGet");
    Operation initialOperation = operation("one.example", 11211, "one");
    Context context = SpymemcachedRequestHolder.init(Context.root(), request);
    SpymemcachedRequestHolder.associateOperation(context, initialOperation);
    SpymemcachedRequestHolder.captureHandlingNode(context, initialOperation);

    Operation firstRetry = operation("two.example", 11212, "one");
    try (SpymemcachedRequestHolder.RetryScope firstRetryScope =
        SpymemcachedRequestHolder.startRetry(initialOperation)) {
      assertThat(firstRetryScope).isNotNull();
      SpymemcachedRequestHolder.associateOperation(Context.current(), firstRetry);
      SpymemcachedRequestHolder.captureHandlingNode(Context.current(), firstRetry);
    }

    try (SpymemcachedRequestHolder.RetryScope secondRetryScope =
        SpymemcachedRequestHolder.startRetry(firstRetry)) {
      assertThat(secondRetryScope).isNotNull();
      Operation secondRetry = operation("three.example", 11213, "one");
      SpymemcachedRequestHolder.associateOperation(Context.current(), secondRetry);
      SpymemcachedRequestHolder.captureHandlingNode(Context.current(), secondRetry);
    }

    request.captureHandlingNodeAddress();
    assertThat(request.getHandlingNodeAddress()).isEqualTo(node("three.example", 11213));
  }

  @Test
  void sequentialPartialRetriesUseLastHandlingNode() {
    MemcachedConnection connection = mock(MemcachedConnection.class);
    SpymemcachedRequest request = SpymemcachedRequest.create(connection, "asyncGetBulk");
    Operation initialOperation = operation("one.example", 11211, "one", "two");
    Context context = SpymemcachedRequestHolder.init(Context.root(), request);
    SpymemcachedRequestHolder.associateOperation(context, initialOperation);
    SpymemcachedRequestHolder.captureHandlingNode(context, initialOperation);

    MemcachedNode firstRetryNode = memcachedNode("two.example", 11212);
    Operation firstKeyRetry = operation(firstRetryNode, "one");
    Operation secondKeyRetry = operation(firstRetryNode, "two");
    try (SpymemcachedRequestHolder.RetryScope firstRetryScope =
        SpymemcachedRequestHolder.startRetry(initialOperation)) {
      assertThat(firstRetryScope).isNotNull();
      SpymemcachedRequestHolder.associateOperation(Context.current(), firstKeyRetry);
      SpymemcachedRequestHolder.captureHandlingNode(Context.current(), firstKeyRetry);
      SpymemcachedRequestHolder.associateOperation(Context.current(), secondKeyRetry);
      SpymemcachedRequestHolder.captureHandlingNode(Context.current(), secondKeyRetry);
    }

    try (SpymemcachedRequestHolder.RetryScope firstKeyRetryScope =
        SpymemcachedRequestHolder.startRetry(firstKeyRetry)) {
      assertThat(firstKeyRetryScope).isNotNull();
      Operation operation = operation("three.example", 11213, "one");
      SpymemcachedRequestHolder.associateOperation(Context.current(), operation);
      SpymemcachedRequestHolder.captureHandlingNode(Context.current(), operation);
    }

    try (SpymemcachedRequestHolder.RetryScope secondKeyRetryScope =
        SpymemcachedRequestHolder.startRetry(secondKeyRetry)) {
      assertThat(secondKeyRetryScope).isNotNull();
      Operation operation = operation("four.example", 11214, "two");
      SpymemcachedRequestHolder.associateOperation(Context.current(), operation);
      SpymemcachedRequestHolder.captureHandlingNode(Context.current(), operation);
    }

    request.captureHandlingNodeAddress();
    assertThat(request.getHandlingNodeAddress()).isEqualTo(node("four.example", 11214));
  }

  @Test
  void optimizedRetryKeepsRequestsSeparateByKey() {
    MemcachedConnection connection = mock(MemcachedConnection.class);
    SpymemcachedRequest firstRequest = SpymemcachedRequest.create(connection, "asyncGet");
    SpymemcachedRequest secondRequest = SpymemcachedRequest.create(connection, "asyncGet");
    Operation firstOperation = operation("one.example", 11211, "one");
    Operation secondOperation = operation("one.example", 11211, "two");
    Context firstContext = SpymemcachedRequestHolder.init(Context.root(), firstRequest);
    Context secondContext = SpymemcachedRequestHolder.init(Context.root(), secondRequest);
    SpymemcachedRequestHolder.associateOperation(firstContext, firstOperation);
    SpymemcachedRequestHolder.captureHandlingNode(firstContext, firstOperation);
    SpymemcachedRequestHolder.associateOperation(secondContext, secondOperation);
    SpymemcachedRequestHolder.captureHandlingNode(secondContext, secondOperation);
    Operation optimizedOperation = operation("one.example", 11211, "one", "two");
    SpymemcachedRequestHolder.propagateOperation(optimizedOperation, firstOperation);
    SpymemcachedRequestHolder.propagateOperation(optimizedOperation, secondOperation);

    try (SpymemcachedRequestHolder.RetryScope retryScope =
        SpymemcachedRequestHolder.startRetry(optimizedOperation)) {
      assertThat(retryScope).isNotNull();
      Operation firstRetry = operation("two.example", 11212, "one");
      SpymemcachedRequestHolder.associateOperation(Context.current(), firstRetry);
      SpymemcachedRequestHolder.captureHandlingNode(Context.current(), firstRetry);
      Operation secondRetry = operation("three.example", 11213, "two");
      SpymemcachedRequestHolder.associateOperation(Context.current(), secondRetry);
      SpymemcachedRequestHolder.captureHandlingNode(Context.current(), secondRetry);
    }

    firstRequest.captureHandlingNodeAddress();
    secondRequest.captureHandlingNodeAddress();
    assertThat(firstRequest.getHandlingNodeAddress()).isEqualTo(node("two.example", 11212));
    assertThat(secondRequest.getHandlingNodeAddress()).isEqualTo(node("three.example", 11213));
  }

  @Test
  void handlingNodeIsHeldPerRequest() {
    MemcachedConnection connection = mock(MemcachedConnection.class);
    SpymemcachedSingletons.setServerTarget(connection, singletonList(node("one.example", 11211)));

    SpymemcachedRequest first = SpymemcachedRequest.create(connection, "asyncGet");
    SpymemcachedRequest second = SpymemcachedRequest.create(connection, "asyncGet");
    first.setHandlingNode(memcachedNode("one.example", 11211));

    second.captureHandlingNodeAddress();
    first.captureHandlingNodeAddress();
    assertThat(second.getHandlingNodeAddress()).isNull();
    assertThat(first.getHandlingNodeAddress()).isEqualTo(node("one.example", 11211));
  }

  @Test
  void concurrentUnkeyedCapturesPreserveMultiNodeAmbiguity() throws Exception {
    MemcachedConnection connection = mock(MemcachedConnection.class);
    SpymemcachedRequest request = SpymemcachedRequest.create(connection, "asyncGet");
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<?> first =
          executor.submit(() -> request.setHandlingNode(memcachedNode("one.example", 11211)));
      Future<?> second =
          executor.submit(() -> request.setHandlingNode(memcachedNode("two.example", 11212)));
      first.get();
      second.get();
    } finally {
      executor.shutdownNow();
    }

    request.captureHandlingNodeAddress();
    assertThat(request.getHandlingNodeAddress()).isNull();
  }

  @Test
  void concurrentDisjointKeyCapturesPreserveMultiNodeAmbiguity() throws Exception {
    MemcachedConnection connection = mock(MemcachedConnection.class);
    SpymemcachedRequest request = SpymemcachedRequest.create(connection, "asyncGetBulk");
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<?> first =
          executor.submit(
              () ->
                  request.setHandlingNode(
                      memcachedNode("one.example", 11211), singletonList("one")));
      Future<?> second =
          executor.submit(
              () ->
                  request.setHandlingNode(
                      memcachedNode("two.example", 11212), singletonList("two")));
      first.get();
      second.get();
    } finally {
      executor.shutdownNow();
    }

    request.captureHandlingNodeAddress();
    assertThat(request.getHandlingNodeAddress()).isNull();
  }

  @Test
  void clearHandlingNodeRemainsAmbiguousAfterLateObservation() {
    MemcachedConnection connection = mock(MemcachedConnection.class);
    SpymemcachedRequest request = SpymemcachedRequest.create(connection, "asyncGet");
    request.setHandlingNode(memcachedNode("old.example", 11211));
    request.clearHandlingNode();
    request.setHandlingNode(memcachedNode("new.example", 11212));

    request.captureHandlingNodeAddress();
    assertThat(request.getHandlingNodeAddress()).isNull();
  }

  @Test
  void sameKeyCaptureUsesTheLatestNode() {
    MemcachedConnection connection = mock(MemcachedConnection.class);
    SpymemcachedRequest request = SpymemcachedRequest.create(connection, "asyncGet");
    request.setHandlingNode(memcachedNode("one.example", 11211), singletonList("key"));
    request.setHandlingNode(memcachedNode("two.example", 11212), singletonList("key"));

    request.captureHandlingNodeAddress();
    assertThat(request.getHandlingNodeAddress()).isEqualTo(node("two.example", 11212));
  }

  @Test
  void addressIsReadOnceForMultipleKeys() {
    MemcachedConnection connection = mock(MemcachedConnection.class);
    SpymemcachedRequest request = SpymemcachedRequest.create(connection, "asyncGetBulk");
    MemcachedNode node = mock(MemcachedNode.class);
    when(node.getSocketAddress())
        .thenReturn(node("one.example", 11211), node("one.example", 11212));

    request.setHandlingNode(node, singletonList("one"));
    request.setHandlingNode(node, singletonList("two"));

    verify(node, never()).getSocketAddress();
    request.captureHandlingNodeAddress();
    verify(node).getSocketAddress();
    assertThat(request.getHandlingNodeAddress()).isEqualTo(node("one.example", 11211));
  }

  @Test
  void mutableNodeAddressIsSampledAtCompletionPreparation() {
    MemcachedConnection connection = mock(MemcachedConnection.class);
    SpymemcachedRequest request = SpymemcachedRequest.create(connection, "asyncGet");
    AtomicReference<InetSocketAddress> address = new AtomicReference<>(node("one.example", 11211));
    MemcachedNode node = mock(MemcachedNode.class);
    when(node.getSocketAddress()).thenAnswer(invocation -> address.get());

    request.setHandlingNode(node);
    address.set(node("two.example", 11212));
    request.captureHandlingNodeAddress();

    assertThat(request.getHandlingNodeAddress()).isEqualTo(node("two.example", 11212));
  }

  @Test
  void mixedKeyedAndUnkeyedCapturesOnDifferentNodesAreAmbiguous() {
    MemcachedConnection connection = mock(MemcachedConnection.class);
    SpymemcachedRequest request = SpymemcachedRequest.create(connection, "asyncGet");

    request.setHandlingNode(memcachedNode("one.example", 11211), singletonList("one"));
    request.setHandlingNode(memcachedNode("two.example", 11212));

    request.captureHandlingNodeAddress();
    assertThat(request.getHandlingNodeAddress()).isNull();
  }

  @Test
  void unkeyedCaptureCannotHideExistingKeyFanout() {
    MemcachedConnection connection = mock(MemcachedConnection.class);
    SpymemcachedRequest request = SpymemcachedRequest.create(connection, "asyncGetBulk");
    MemcachedNode firstNode = memcachedNode("one.example", 11211);

    request.setHandlingNode(firstNode, singletonList("one"));
    request.setHandlingNode(memcachedNode("two.example", 11212), singletonList("two"));
    request.setHandlingNode(firstNode);

    request.captureHandlingNodeAddress();
    assertThat(request.getHandlingNodeAddress()).isNull();
  }

  @Test
  void mixedKeyedAndUnkeyedCapturesOnTheSameNodeUseTheLatestAddress() {
    MemcachedConnection connection = mock(MemcachedConnection.class);
    SpymemcachedRequest request = SpymemcachedRequest.create(connection, "asyncGet");
    MemcachedNode node = mock(MemcachedNode.class);
    when(node.getSocketAddress())
        .thenReturn(node("one.example", 11211), node("one.example", 11212));

    request.setHandlingNode(node, singletonList("one"));
    request.setHandlingNode(node);

    request.captureHandlingNodeAddress();
    assertThat(request.getHandlingNodeAddress()).isEqualTo(node("one.example", 11211));
  }

  @Test
  void lateInitialObservationAfterRetryRemainsAmbiguous() {
    MemcachedConnection connection = mock(MemcachedConnection.class);
    SpymemcachedRequest request = SpymemcachedRequest.create(connection, "asyncGet");
    request.setRetryHandlingNode(memcachedNode("retry.example", 11212));
    request.setHandlingNode(memcachedNode("late.example", 11213));

    request.captureHandlingNodeAddress();
    assertThat(request.getHandlingNodeAddress()).isNull();
  }

  @Test
  void lateInitialKeyObservationAfterRetryUsesItsSelectedNode() {
    MemcachedConnection connection = mock(MemcachedConnection.class);
    SpymemcachedRequest request = SpymemcachedRequest.create(connection, "asyncGetBulk");
    request.setRetryHandlingNode(memcachedNode("retry.example", 11212));
    request.setHandlingNode(memcachedNode("late.example", 11213), singletonList("keyB"));

    request.captureHandlingNodeAddress();
    assertThat(request.getHandlingNodeAddress()).isEqualTo(node("late.example", 11213));
  }

  @Test
  void retryNodeAndLaterSameNodeObservationRemainAmbiguousWithOtherKey() {
    MemcachedConnection connection = mock(MemcachedConnection.class);
    SpymemcachedRequest request = SpymemcachedRequest.create(connection, "asyncGetBulk");
    MemcachedNode retryNode = memcachedNode("retry.example", 11212);
    MemcachedNode laterNode = memcachedNode("late.example", 11213);

    request.setRetryHandlingNode(retryNode);
    request.setHandlingNode(laterNode, singletonList("keyB"));
    request.setHandlingNode(laterNode);

    request.captureHandlingNodeAddress();
    assertThat(request.getHandlingNodeAddress()).isNull();
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

  private static Operation operation(String host, int port, String... keys) {
    return operation(memcachedNode(host, port), keys);
  }

  private static Operation operation(MemcachedNode node, String... keys) {
    KeyedOperation operation = mock(KeyedOperation.class);
    when(operation.getHandlingNode()).thenReturn(node);
    when(operation.getKeys()).thenReturn(asList(keys));
    return operation;
  }

  private static InetSocketAddress node(String host, int port) {
    return InetSocketAddress.createUnresolved(host, port);
  }
}
