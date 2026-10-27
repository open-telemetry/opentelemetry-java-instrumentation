/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spymemcached.v2_12;

import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.semconv.network.ServerAttributesExtractor;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import net.spy.memcached.MemcachedConnection;
import net.spy.memcached.MemcachedNode;
import net.spy.memcached.internal.BulkGetFuture;
import net.spy.memcached.internal.GetFuture;
import net.spy.memcached.internal.OperationFuture;
import net.spy.memcached.ops.OperationStatus;
import net.spy.memcached.ops.StatusCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class SpymemcachedAttributesGetterTest {

  private final SpymemcachedAttributesGetter getter = new SpymemcachedAttributesGetter();

  @ParameterizedTest
  @EnumSource(
      value = StatusCode.class,
      names = {
        "SUCCESS",
        "ERR_NOT_FOUND",
        "ERR_EXISTS",
        "ERR_2BIG",
        "ERR_INVAL",
        "ERR_NOT_STORED",
        "ERR_DELTA_BADVAL",
        "ERR_NOT_MY_VBUCKET",
        "ERR_UNKNOWN_COMMAND",
        "ERR_NO_MEM",
        "ERR_NOT_SUPPORTED",
        "ERR_INTERNAL",
        "ERR_BUSY",
        "ERR_TEMP_FAIL",
        "CANCELLED",
        "INTERRUPTED",
        "TIMEDOUT",
        "ERR_CLIENT"
      })
  void databaseOutcomeStatusesAreReportedRegardlessOfSuccess(StatusCode code) {
    OperationFuture<?> future = mock(OperationFuture.class);
    when(future.getStatus()).thenReturn(new OperationStatus(true, "status", code));

    assertThat(getter.getDbResponseStatusCode(null, future, null)).isEqualTo(code.name());
  }

  @Test
  void responseStatusDoesNotChangeErrorTypeClassification() {
    OperationFuture<?> future = mock(OperationFuture.class);
    when(future.getStatus())
        .thenReturn(new OperationStatus(false, "too large", StatusCode.ERR_2BIG));
    assertThat(getter.getDbResponseStatusCode(null, future, null)).isEqualTo("ERR_2BIG");
    assertThat(getter.getErrorType(null, future, null)).isEqualTo("ERR_2BIG");

    when(future.getStatus()).thenReturn(new OperationStatus(true, "stored", StatusCode.SUCCESS));
    assertThat(getter.getDbResponseStatusCode(null, future, null)).isEqualTo("SUCCESS");
    when(future.getStatus())
        .thenReturn(new OperationStatus(false, "cancelled", StatusCode.CANCELLED));
    assertThat(getter.getDbResponseStatusCode(null, future, null)).isEqualTo("CANCELLED");
    assertThat(getter.getErrorType(null, future, null)).isNull();
    assertThat(getter.getDbResponseStatusCode(null, null, new IllegalStateException("failed")))
        .isNull();
    assertThat(getter.getDbResponseStatusCode(null, null, null)).isNull();
  }

  @Test
  void operationFutureStatusIsAvailableWhenResultRetrievalFails() {
    OperationFuture<?> future = mock(OperationFuture.class);
    when(future.getStatus())
        .thenReturn(new OperationStatus(false, "too large", StatusCode.ERR_2BIG));
    Throwable error = new ExecutionException(new IllegalStateException());

    assertThat(getter.getDbResponseStatusCode(null, future, error)).isEqualTo("ERR_2BIG");
    assertThat(getter.getErrorType(null, future, error))
        .isEqualTo(ExecutionException.class.getName());
  }

  @Test
  void getFutureStatusIsAvailableWhenResultRetrievalFails() {
    GetFuture<?> future = mock(GetFuture.class);
    when(future.getStatus())
        .thenReturn(new OperationStatus(false, "too large", StatusCode.ERR_2BIG));
    Throwable error = new ExecutionException(new IllegalStateException());

    assertThat(getter.getDbResponseStatusCode(null, future, error)).isEqualTo("ERR_2BIG");
    assertThat(getter.getErrorType(null, future, error))
        .isEqualTo(ExecutionException.class.getName());
    assertThat(getter.getErrorType(null, future, null)).isNull();
  }

  @ParameterizedTest
  @EnumSource(
      value = StatusCode.class,
      names = {"TIMEDOUT", "INTERRUPTED", "ERR_CLIENT"})
  void clientStatusesAreAvailableWithoutChangingErrorType(StatusCode code) {
    OperationFuture<?> future = mock(OperationFuture.class);
    when(future.getStatus()).thenReturn(new OperationStatus(false, "client failure", code));

    assertThat(getter.getErrorType(null, future, null)).isEqualTo(code.name());
    assertThat(getter.getDbResponseStatusCode(null, future, null)).isEqualTo(code.name());
  }

  @Test
  void cancelledFutureRetainsItsResponseStatus() {
    OperationFuture<?> future = mock(OperationFuture.class);
    when(future.isCancelled()).thenReturn(true);
    when(future.getStatus())
        .thenReturn(new OperationStatus(false, "cancelled", StatusCode.CANCELLED));

    assertThat(getter.getDbResponseStatusCode(null, future, null)).isEqualTo("CANCELLED");
    assertThat(getter.getErrorType(null, future, null)).isNull();
  }

  @Test
  void bulkFutureStatusIsAvailable() {
    BulkGetFuture<?> future = mock(BulkGetFuture.class);
    when(future.getStatus()).thenReturn(new OperationStatus(true, "stored", StatusCode.SUCCESS));

    assertThat(getter.getDbResponseStatusCode(null, future, null)).isEqualTo("SUCCESS");
  }

  @Test
  void singleDefaultPortIsOmitted() {
    SpymemcachedRequest request = request(singletonList(node("one.example", 11211)));
    request.setHandlingNode(memcachedNode("selected.example", 11212));

    assertThat(getter.getServerAddress(request)).isEqualTo("one.example");
    assertThat(getter.getServerPort(request)).isEqualTo(null);
  }

  @Test
  void customPortsStayInlineInMultiEndpointTelemetry() {
    SpymemcachedRequest request =
        request(asList(node("one.example", 11212), node("two.example", 11212)));
    request.setHandlingNode(memcachedNode("two.example", 11212));

    assertThat(getter.getServerAddress(request)).isEqualTo("one.example:11212,two.example:11212");
    assertThat(getter.getServerPort(request)).isEqualTo(null);
  }

  @Test
  void mixedPortsStayInlineInTelemetry() {
    SpymemcachedRequest request =
        request(asList(node("one.example", 11211), node("two.example", 11212)));
    request.setHandlingNode(memcachedNode("selected.example", 11213));

    assertThat(getter.getServerAddress(request)).isEqualTo("one.example:11211,two.example:11212");
    assertThat(getter.getServerPort(request)).isEqualTo(null);
  }

  @Test
  void clientWithoutAConfiguredTargetDoesNotUseHandlingNodeAsServer() {
    SpymemcachedRequest request =
        SpymemcachedRequest.create(mock(MemcachedConnection.class), "asyncGet");
    request.setHandlingNode(memcachedNode("one.example", 11211));

    assertThat(getter.getServerAddress(request)).isEqualTo(null);
    assertThat(getter.getServerPort(request)).isEqualTo(null);
  }

  @Test
  void selectedNodeIsNotUsedAsAnUnconfiguredServer() {
    MemcachedConnection connection = mock(MemcachedConnection.class);
    SpymemcachedRequest request = SpymemcachedRequest.create(connection, "asyncGet");
    request.setHandlingNode(memcachedNode("selected.example", 11212));
    AttributesBuilder attributes = Attributes.builder();

    extractServerAttributes(attributes, request);

    Attributes result = attributes.build();
    assertThat(result.get(SERVER_ADDRESS)).isEqualTo(null);
    assertThat(result.get(SERVER_PORT)).isEqualTo(null);
  }

  @Test
  void selectedNodeDoesNotOverwriteConfiguredTarget() {
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
    assertThat(result.get(SERVER_ADDRESS)).isEqualTo("one.example:11212,two.example:11212");
    assertThat(result.get(SERVER_PORT)).isEqualTo(null);
    assertThat(request.getServerTarget().getAddress())
        .isEqualTo("one.example:11212,two.example:11212");
  }

  @Test
  void resolvedHandlingNodeIsTheNetworkPeer() throws UnknownHostException {
    SpymemcachedRequest request = request(singletonList(node("one.example", 11211)));
    InetSocketAddress peer =
        new InetSocketAddress(InetAddress.getByAddress(new byte[] {10, 20, 30, 40}), 11211);
    request.setHandlingNode(memcachedNode(peer));

    assertThat(getter.getNetworkPeerInetSocketAddress(request, null)).isEqualTo(peer);
    assertThat(getter.getNetworkPeerAddress(request, null)).isEqualTo("10.20.30.40");
    assertThat(getter.getNetworkPeerPort(request, null)).isEqualTo(11211);
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
  void nonInetHandlingNodeIsNotReportedAsNetworkPeer() {
    SpymemcachedRequest request = request(singletonList(node("one.example", 11211)));
    MemcachedNode node = mock(MemcachedNode.class);
    when(node.getSocketAddress())
        .thenReturn(
            new SocketAddress() {
              private static final long serialVersionUID = 1L;
            });
    request.setHandlingNode(node);

    assertThat(getter.getNetworkPeerInetSocketAddress(request, null)).isNull();
    assertThat(getter.getNetworkPeerAddress(request, null)).isNull();
    assertThat(getter.getNetworkPeerPort(request, null)).isNull();
  }

  @Test
  void addressAndPortReadsUseTheEnqueueTimeSnapshot() throws UnknownHostException {
    SpymemcachedRequest request = request(singletonList(node("one.example", 11211)));
    InetSocketAddress first =
        new InetSocketAddress(InetAddress.getByAddress(new byte[] {10, 20, 30, 40}), 11211);
    InetSocketAddress second =
        new InetSocketAddress(InetAddress.getByAddress(new byte[] {10, 20, 30, 41}), 11212);
    AtomicReference<InetSocketAddress> address = new AtomicReference<>(first);
    MemcachedNode node = mock(MemcachedNode.class);
    when(node.getSocketAddress()).thenAnswer(invocation -> address.get());

    request.setHandlingNode(node);
    address.set(second);

    assertThat(getter.getNetworkPeerAddress(request, null)).isEqualTo(first.getHostString());
    assertThat(getter.getNetworkPeerPort(request, null)).isEqualTo(first.getPort());
    verify(node).getSocketAddress();
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

  private void extractServerAttributes(AttributesBuilder attributes, SpymemcachedRequest request) {
    ServerAttributesExtractor.create(getter).onStart(attributes, Context.root(), request);
  }

  private static InetSocketAddress node(String host, int port) {
    return InetSocketAddress.createUnresolved(host, port);
  }
}
