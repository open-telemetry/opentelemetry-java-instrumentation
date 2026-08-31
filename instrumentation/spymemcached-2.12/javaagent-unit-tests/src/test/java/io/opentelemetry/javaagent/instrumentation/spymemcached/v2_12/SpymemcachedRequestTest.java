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
import net.spy.memcached.ops.KeyedOperation;
import net.spy.memcached.ops.Operation;
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
  void initialOperationsOnSeveralNodesHaveNoHandlingNode() {
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
  void retryUsesTheNodeAssignedOnTheConnectionThread() {
    MemcachedConnection connection = mock(MemcachedConnection.class);
    SpymemcachedServerTargets.capture(
        connection, asList(node("one.example", 11211), node("two.example", 11212)));
    SpymemcachedRequest request = SpymemcachedRequest.create(connection, "asyncGet");
    Operation initialOperation = operation("one.example", 11211);
    Context context = SpymemcachedRequestHolder.init(Context.root(), request);
    SpymemcachedRequestHolder.associateOperation(context, initialOperation);
    SpymemcachedRequestHolder.captureHandlingNode(context, initialOperation);

    SpymemcachedRequestHolder.RetryScope retryScope =
        SpymemcachedRequestHolder.startRetry(initialOperation);
    assertThat(retryScope).isNotNull();
    try {
      Operation retryOperation = operation("two.example", 11212);
      SpymemcachedRequestHolder.associateOperation(Context.current(), retryOperation);
      SpymemcachedRequestHolder.captureHandlingNode(Context.current(), retryOperation);
    } finally {
      retryScope.close();
    }

    assertThat(request.getHandlingNodeAddress()).isEqualTo(node("two.example", 11212));
    assertThat(request.getServerTarget().getAddress())
        .isEqualTo("one.example:11211,two.example:11212");
  }

  @Test
  void partialBulkRetryHasNoHandlingNode() {
    MemcachedConnection connection = mock(MemcachedConnection.class);
    SpymemcachedRequest request = SpymemcachedRequest.create(connection, "asyncGetBulk");
    Operation initialOperation = operation("one.example", 11211, "one", "two");
    Context context = SpymemcachedRequestHolder.init(Context.root(), request);
    SpymemcachedRequestHolder.associateOperation(context, initialOperation);
    SpymemcachedRequestHolder.captureHandlingNode(context, initialOperation);

    SpymemcachedRequestHolder.RetryScope retryScope =
        SpymemcachedRequestHolder.startRetry(initialOperation);
    assertThat(retryScope).isNotNull();
    try {
      Operation retryOperation = operation("two.example", 11212, "two");
      SpymemcachedRequestHolder.associateOperation(Context.current(), retryOperation);
      SpymemcachedRequestHolder.captureHandlingNode(Context.current(), retryOperation);
    } finally {
      retryScope.close();
    }

    assertThat(request.getHandlingNodeAddress()).isNull();
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
    SpymemcachedRequestHolder.RetryScope retryScope =
        SpymemcachedRequestHolder.startRetry(initialOperation);
    assertThat(retryScope).isNotNull();
    try {
      Operation firstRetry = operation(retryNode, "one");
      SpymemcachedRequestHolder.associateOperation(Context.current(), firstRetry);
      SpymemcachedRequestHolder.captureHandlingNode(Context.current(), firstRetry);
      Operation secondRetry = operation(retryNode, "two");
      SpymemcachedRequestHolder.associateOperation(Context.current(), secondRetry);
      SpymemcachedRequestHolder.captureHandlingNode(Context.current(), secondRetry);
    } finally {
      retryScope.close();
    }

    assertThat(request.getHandlingNodeAddress()).isEqualTo(node("two.example", 11212));
  }

  @Test
  void retryOntoSeveralNodesHasNoHandlingNode() {
    MemcachedConnection connection = mock(MemcachedConnection.class);
    SpymemcachedRequest request = SpymemcachedRequest.create(connection, "asyncGetBulk");
    Operation initialOperation = operation("one.example", 11211);
    Context context = SpymemcachedRequestHolder.init(Context.root(), request);
    SpymemcachedRequestHolder.associateOperation(context, initialOperation);
    SpymemcachedRequestHolder.captureHandlingNode(context, initialOperation);

    SpymemcachedRequestHolder.RetryScope retryScope =
        SpymemcachedRequestHolder.startRetry(initialOperation);
    assertThat(retryScope).isNotNull();
    try {
      Operation firstRetry = operation("two.example", 11212);
      SpymemcachedRequestHolder.associateOperation(Context.current(), firstRetry);
      SpymemcachedRequestHolder.captureHandlingNode(Context.current(), firstRetry);
      Operation secondRetry = operation("three.example", 11213);
      SpymemcachedRequestHolder.associateOperation(Context.current(), secondRetry);
      SpymemcachedRequestHolder.captureHandlingNode(Context.current(), secondRetry);
    } finally {
      retryScope.close();
    }

    assertThat(request.getHandlingNodeAddress()).isNull();
  }

  @Test
  void retryOntoSeveralNodesAcrossRetryScopesHasNoHandlingNode() {
    MemcachedConnection connection = mock(MemcachedConnection.class);
    SpymemcachedRequest request = SpymemcachedRequest.create(connection, "asyncGet");
    Operation initialOperation = operation("one.example", 11211);
    Context context = SpymemcachedRequestHolder.init(Context.root(), request);
    SpymemcachedRequestHolder.associateOperation(context, initialOperation);
    SpymemcachedRequestHolder.captureHandlingNode(context, initialOperation);

    Operation firstRetry = operation("two.example", 11212);
    SpymemcachedRequestHolder.RetryScope firstRetryScope =
        SpymemcachedRequestHolder.startRetry(initialOperation);
    assertThat(firstRetryScope).isNotNull();
    try {
      SpymemcachedRequestHolder.associateOperation(Context.current(), firstRetry);
      SpymemcachedRequestHolder.captureHandlingNode(Context.current(), firstRetry);
    } finally {
      firstRetryScope.close();
    }

    SpymemcachedRequestHolder.RetryScope secondRetryScope =
        SpymemcachedRequestHolder.startRetry(firstRetry);
    assertThat(secondRetryScope).isNotNull();
    try {
      Operation secondRetry = operation("three.example", 11213);
      SpymemcachedRequestHolder.associateOperation(Context.current(), secondRetry);
      SpymemcachedRequestHolder.captureHandlingNode(Context.current(), secondRetry);
    } finally {
      secondRetryScope.close();
    }

    assertThat(request.getHandlingNodeAddress()).isNull();
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

    SpymemcachedRequestHolder.RetryScope retryScope =
        SpymemcachedRequestHolder.startRetry(optimizedOperation);
    assertThat(retryScope).isNotNull();
    try {
      Operation firstRetry = operation("two.example", 11212, "one");
      SpymemcachedRequestHolder.associateOperation(Context.current(), firstRetry);
      SpymemcachedRequestHolder.captureHandlingNode(Context.current(), firstRetry);
      Operation secondRetry = operation("three.example", 11213, "two");
      SpymemcachedRequestHolder.associateOperation(Context.current(), secondRetry);
      SpymemcachedRequestHolder.captureHandlingNode(Context.current(), secondRetry);
    } finally {
      retryScope.close();
    }

    assertThat(firstRequest.getHandlingNodeAddress()).isEqualTo(node("two.example", 11212));
    assertThat(secondRequest.getHandlingNodeAddress()).isEqualTo(node("three.example", 11213));
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

  private static Operation operation(String host, int port) {
    return operation(host, port, new String[0]);
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
