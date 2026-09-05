/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spymemcached.v2_12;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.context.Context;
import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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

    assertThat(second.getHandlingNodeAddress()).isNull();
    assertThat(first.getHandlingNodeAddress()).isEqualTo(node("one.example", 11211));
  }

  @Test
  void concurrentDisjointKeyCapturesPreserveMultiNodeAmbiguity() throws Exception {
    MemcachedConnection connection = mock(MemcachedConnection.class);
    SpymemcachedRequest request = SpymemcachedRequest.create(connection, "asyncGetBulk");
    CountDownLatch capturesStarted = new CountDownLatch(2);
    CountDownLatch releaseCaptures = new CountDownLatch(1);
    MemcachedNode firstNode =
        blockingMemcachedNode("one.example", 11211, capturesStarted, releaseCaptures);
    MemcachedNode secondNode =
        blockingMemcachedNode("two.example", 11212, capturesStarted, releaseCaptures);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<?> first =
          executor.submit(() -> request.setHandlingNode(firstNode, singletonList("one")));
      Future<?> second =
          executor.submit(() -> request.setHandlingNode(secondNode, singletonList("two")));
      assertThat(capturesStarted.await(10, TimeUnit.SECONDS)).isTrue();
      releaseCaptures.countDown();
      first.get();
      second.get();
    } finally {
      executor.shutdownNow();
    }

    assertThat(request.getHandlingNodeAddress()).isNull();
  }

  @Test
  void staleCaptureBeforeClearCannotRestoreHandlingNode() throws Exception {
    MemcachedConnection connection = mock(MemcachedConnection.class);
    SpymemcachedRequest request = SpymemcachedRequest.create(connection, "asyncGet");
    CountDownLatch captureStarted = new CountDownLatch(1);
    CountDownLatch releaseCapture = new CountDownLatch(1);
    MemcachedNode oldNode =
        blockingMemcachedNode("old.example", 11211, captureStarted, releaseCapture);
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      Future<?> capture = executor.submit(() -> request.setHandlingNode(oldNode));
      assertThat(captureStarted.await(10, TimeUnit.SECONDS)).isTrue();
      request.clearHandlingNode();
      releaseCapture.countDown();
      capture.get();
    } finally {
      executor.shutdownNow();
    }

    assertThat(request.getHandlingNodeAddress()).isNull();
  }

  @Test
  void sameKeyCaptureUsesCallOrderWhenAddressReadsCompleteOutOfOrder() throws Exception {
    MemcachedConnection connection = mock(MemcachedConnection.class);
    SpymemcachedRequest request = SpymemcachedRequest.create(connection, "asyncGet");
    CountDownLatch firstCaptureStarted = new CountDownLatch(1);
    CountDownLatch secondCaptureStarted = new CountDownLatch(1);
    CountDownLatch releaseFirstCapture = new CountDownLatch(1);
    CountDownLatch releaseSecondCapture = new CountDownLatch(1);
    MemcachedNode firstNode =
        blockingMemcachedNode(
            "one.example", 11211, firstCaptureStarted, releaseFirstCapture);
    MemcachedNode secondNode =
        blockingMemcachedNode(
            "two.example", 11212, secondCaptureStarted, releaseSecondCapture);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<?> first =
          executor.submit(() -> request.setHandlingNode(firstNode, singletonList("key")));
      assertThat(firstCaptureStarted.await(10, TimeUnit.SECONDS)).isTrue();
      Future<?> second =
          executor.submit(() -> request.setHandlingNode(secondNode, singletonList("key")));
      assertThat(secondCaptureStarted.await(10, TimeUnit.SECONDS)).isTrue();
      releaseSecondCapture.countDown();
      second.get();
      releaseFirstCapture.countDown();
      first.get();
    } finally {
      executor.shutdownNow();
    }

    assertThat(request.getHandlingNodeAddress()).isEqualTo(node("two.example", 11212));
  }

  @Test
  void staleCaptureBeforeRetryCannotRestoreThePreviousPeer() throws Exception {
    MemcachedConnection connection = mock(MemcachedConnection.class);
    SpymemcachedRequest request = SpymemcachedRequest.create(connection, "asyncGet");
    CountDownLatch captureStarted = new CountDownLatch(1);
    CountDownLatch releaseCapture = new CountDownLatch(1);
    MemcachedNode oldNode =
        blockingMemcachedNode("old.example", 11211, captureStarted, releaseCapture);
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      Future<?> capture = executor.submit(() -> request.setHandlingNode(oldNode));
      assertThat(captureStarted.await(10, TimeUnit.SECONDS)).isTrue();
      request.setRetryHandlingNode(memcachedNode("retry.example", 11212));
      releaseCapture.countDown();
      capture.get();
    } finally {
      executor.shutdownNow();
    }

    assertThat(request.getHandlingNodeAddress()).isEqualTo(node("retry.example", 11212));
  }

  private static MemcachedNode memcachedNode(String host, int port) {
    MemcachedNode node = mock(MemcachedNode.class);
    when(node.getSocketAddress()).thenReturn(node(host, port));
    return node;
  }

  private static MemcachedNode blockingMemcachedNode(
      String host, int port, CountDownLatch started, CountDownLatch release) {
    MemcachedNode node = mock(MemcachedNode.class);
    when(node.getSocketAddress())
        .thenAnswer(
            invocation -> {
              started.countDown();
              release.await();
              return node(host, port);
            });
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
