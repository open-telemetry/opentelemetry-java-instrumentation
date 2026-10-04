/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v4_0;

import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_TYPE;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.datastax.oss.driver.api.core.cql.ExecutionInfo;
import com.datastax.oss.driver.api.core.metadata.EndPoint;
import com.datastax.oss.driver.api.core.metadata.Node;
import com.datastax.oss.driver.api.core.session.Session;
import com.datastax.oss.driver.internal.core.metadata.DefaultEndPoint;
import com.datastax.oss.driver.internal.core.metadata.SniEndPoint;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTarget;
import io.opentelemetry.instrumentation.api.semconv.network.ServerAttributesExtractor;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// The Cassandra test container cannot exercise SNI. These tests use driver 4.3.1 to cover the SNI
// APIs that the 4.0 javaagent accesses by reflection.
@ExtendWith(MockitoExtension.class)
class CassandraEndpointAttributesTest {

  @Mock private ExecutionInfo executionInfo;
  @Mock private Node coordinator;
  @Mock private EndPoint customEndPoint;
  @Mock private SniEndPoint sniEndPoint;
  @Mock private Session session;

  @Test
  void unconfiguredSessionDoesNotUseTheCoordinatorAsServer() {
    Attributes attributes = serverAttributes(null);

    assertThat(attributes.get(SERVER_ADDRESS)).isNull();
    assertThat(attributes.get(SERVER_PORT)).isNull();
    verify(coordinator, never()).getEndPoint();
  }

  @Test
  void singleDefaultPortContactPointOmitsItsPort() throws UnknownHostException {
    Attributes attributes =
        serverAttributes(CassandraServerTarget.of(singletonList("cassandra.example.com:9042")));

    assertThat(attributes.get(SERVER_ADDRESS)).isEqualTo("cassandra.example.com");
    assertThat(attributes.get(SERVER_PORT)).isNull();
  }

  @Test
  void severalContactPointsAreOneTargetWithoutAPort() throws UnknownHostException {
    Attributes attributes =
        serverAttributes(CassandraServerTarget.of(asList("node1.example.com:9042", "[::1]:9042")));

    assertThat(attributes.get(SERVER_ADDRESS)).isEqualTo("::1,node1.example.com");
    assertThat(attributes.get(SERVER_PORT)).isNull();
  }

  @Test
  void configuredTargetIsAvailableWithoutExecutionInfo() {
    CassandraRequest request =
        CassandraRequest.create(
            session,
            CassandraServerTarget.of(singletonList("cassandra.example.com:9042")),
            "SELECT 1");
    AttributesBuilder builder = Attributes.builder();

    ServerAttributesExtractor.create(new CassandraSqlAttributesGetter())
        .onStart(builder, Context.root(), request);

    Attributes attributes = builder.build();
    assertThat(attributes.get(SERVER_ADDRESS)).isEqualTo("cassandra.example.com");
    assertThat(attributes.get(SERVER_PORT)).isNull();
  }

  @Test
  void sniEndPointPreservesTheConfiguredTarget() {
    Attributes attributes =
        serverAttributes(CassandraServerTarget.of(singletonList("proxy.example.com:29042")));

    assertThat(attributes.get(SERVER_ADDRESS)).isEqualTo("proxy.example.com");
    assertThat(attributes.get(SERVER_PORT)).isEqualTo(29042L);
    verify(coordinator, never()).getEndPoint();
  }

  @Test
  void defaultEndPointRecordsTheCoordinatorAsNetworkPeer() throws UnknownHostException {
    InetSocketAddress coordinatorAddress = resolved(9042);
    when(coordinator.getEndPoint()).thenReturn(new DefaultEndPoint(coordinatorAddress));
    when(executionInfo.getCoordinator()).thenReturn(coordinator);
    CassandraRequest request = CassandraRequest.create(session, null, "SELECT 1");

    InetSocketAddress peerAddress =
        new CassandraSqlAttributesGetter().getNetworkPeerInetSocketAddress(request, executionInfo);

    assertThat(peerAddress).isEqualTo(coordinatorAddress);
  }

  @Test
  void stableSniEndPointDoesNotResolveNetworkPeerAddress() throws UnknownHostException {
    when(coordinator.getEndPoint()).thenReturn(sniEndPoint);
    when(executionInfo.getCoordinator()).thenReturn(coordinator);
    CassandraRequest request = CassandraRequest.create(session, null, "SELECT 1");

    InetSocketAddress peerAddress =
        new CassandraSqlAttributesGetter().getNetworkPeerInetSocketAddress(request, executionInfo);

    assertThat(peerAddress).isNull();
    verify(sniEndPoint, never()).resolve();
  }

  @Test
  void sniPeerComesFromTheResponse() throws UnknownHostException {
    InetSocketAddress responsePeer = resolved(29042);
    CassandraResponsePeers.setExecutionInfoPeer(executionInfo, responsePeer);
    CassandraSqlAttributesGetter getter = new CassandraSqlAttributesGetter();
    assertThat(getter.getNetworkPeerInetSocketAddress(null, executionInfo)).isEqualTo(responsePeer);
    verify(executionInfo, never()).getCoordinator();
    verifyNoInteractions(coordinator);
  }

  @Test
  void customEndPointIsNotResolvedForStableNetworkPeerFallback() throws UnknownHostException {
    when(executionInfo.getCoordinator()).thenReturn(coordinator);
    when(coordinator.getEndPoint()).thenReturn(customEndPoint);
    CassandraSqlAttributesGetter getter = new CassandraSqlAttributesGetter();
    InetSocketAddress peer = getter.getNetworkPeerInetSocketAddress(null, executionInfo);

    assertThat(peer).isNull();
    verify(customEndPoint, never()).resolve();
  }

  @Test
  void unresolvedDefaultEndPointDoesNotReportNetworkPeer() {
    InetSocketAddress unresolvedPeer = InetSocketAddress.createUnresolved("node.example.com", 9042);
    when(executionInfo.getCoordinator()).thenReturn(coordinator);
    when(coordinator.getEndPoint()).thenReturn(new DefaultEndPoint(unresolvedPeer));

    CassandraSqlAttributesGetter getter = new CassandraSqlAttributesGetter();

    assertThat(getter.getNetworkPeerInetSocketAddress(null, executionInfo)).isEqualTo(null);
  }

  @Test
  void networkPeerIsResolvedAddressUnderDefaultEndPoint() throws UnknownHostException {
    when(executionInfo.getCoordinator()).thenReturn(coordinator);
    when(coordinator.getEndPoint()).thenReturn(new DefaultEndPoint(resolved(9042)));

    CassandraSqlAttributesGetter getter = new CassandraSqlAttributesGetter();
    InetSocketAddress peer = getter.getNetworkPeerInetSocketAddress(null, executionInfo);

    assertThat(peer).isNotNull();
    assertThat(peer.getHostString()).isEqualTo("127.0.0.1");
    assertThat(peer.getPort()).isEqualTo(9042);
  }

  @Test
  void responsePeerTakesPrecedenceOverCoordinator() throws UnknownHostException {
    InetSocketAddress responsePeer = resolved(19042);
    CassandraResponsePeers.setExecutionInfoPeer(executionInfo, responsePeer);
    CassandraSqlAttributesGetter getter = new CassandraSqlAttributesGetter();

    assertThat(getter.getNetworkPeerInetSocketAddress(null, executionInfo)).isEqualTo(responsePeer);
    verify(executionInfo, never()).getCoordinator();
    verifyNoInteractions(coordinator);
  }

  @Test
  void emittedNetworkAttributesUseTheResponsePeer() throws UnknownHostException {
    InetSocketAddress responsePeer = resolved(19042);
    CassandraResponsePeers.setExecutionInfoPeer(executionInfo, responsePeer);
    AttributesBuilder attributes = Attributes.builder();

    SqlClientAttributesExtractor.create(new CassandraSqlAttributesGetter())
        .onEnd(attributes, Context.root(), null, executionInfo, null);

    Attributes result = attributes.build();
    assertThat(result.get(NETWORK_PEER_ADDRESS)).isEqualTo("127.0.0.1");
    assertThat(result.get(NETWORK_PEER_PORT)).isEqualTo(19042L);
    assertThat(result.get(NETWORK_TYPE)).isEqualTo(null);
  }

  private Attributes serverAttributes(DbServerTarget serverTarget) {
    CassandraRequest request = CassandraRequest.create(session, serverTarget, "SELECT 1");
    AttributesBuilder startAttributes = Attributes.builder();
    ServerAttributesExtractor.create(new CassandraSqlAttributesGetter())
        .onStart(startAttributes, Context.root(), request);
    AttributesBuilder endAttributes = Attributes.builder();
    CassandraAttributesExtractor.updateServerAddressAndPort(endAttributes, coordinator);
    return Attributes.builder()
        .putAll(startAttributes.build())
        .putAll(endAttributes.build())
        .build();
  }

  // Build resolved addresses from raw loopback bytes so getHostString returns the literal without
  // a lookup. Do not build an SNI proxy address this way, because SniEndPoint.resolve() reads
  // getHostName(), which reverse-resolves an address built from bytes.
  private static InetSocketAddress resolved(int port) throws UnknownHostException {
    return new InetSocketAddress(InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), port);
  }
}
