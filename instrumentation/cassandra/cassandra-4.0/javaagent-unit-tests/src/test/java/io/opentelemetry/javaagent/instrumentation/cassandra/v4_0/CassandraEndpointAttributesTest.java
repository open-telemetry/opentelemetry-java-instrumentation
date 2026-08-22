/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v4_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.datastax.oss.driver.api.core.cql.ExecutionInfo;
import com.datastax.oss.driver.api.core.metadata.EndPoint;
import com.datastax.oss.driver.api.core.metadata.Node;
import com.datastax.oss.driver.internal.core.metadata.DefaultEndPoint;
import com.datastax.oss.driver.internal.core.metadata.SniEndPoint;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// The proxied (SNI) path cannot be exercised against the Cassandra test container, so this unit
// test covers the endpoint-to-address mapping directly.
@ExtendWith(MockitoExtension.class)
class CassandraEndpointAttributesTest {

  // SniEndPoint.resolve() always looks the proxy host up with
  // InetAddress.getAllByName(proxyAddress.getHostName()), so an unresolved literal keeps every test
  // offline and deterministic. A resolved address would make getHostName() do a reverse dns lookup
  // and the driver would then resolve whatever name came back.
  private static final InetSocketAddress PROXY_ADDRESS =
      InetSocketAddress.createUnresolved("127.0.0.1", 29042);

  @Mock private ExecutionInfo executionInfo;
  @Mock private Node coordinator;
  @Mock private EndPoint customEndPoint;

  @Test
  void defaultEndPointUsesResolvedAddressForServer() throws UnknownHostException {
    DefaultEndPoint endPoint = new DefaultEndPoint(resolved(9042));
    when(coordinator.getEndPoint()).thenReturn(endPoint);

    Attributes attributes = serverAttributes();

    assertThat(attributes.get(SERVER_ADDRESS)).isEqualTo("127.0.0.1");
    assertThat(attributes.get(SERVER_PORT)).isEqualTo(9042L);
  }

  @Test
  void sniEndPointUsesBroadcastRpcAddressForServer() {
    // The proxy the client actually connects to differs from the node behind it. Under the stable
    // conventions the broadcast RPC address is recorded; under the frozen old conventions the proxy
    // returned by resolve() is recorded, so each mode pins a different value.
    SniEndPoint endPoint = new SniEndPoint(PROXY_ADDRESS, "host-id");
    when(coordinator.getEndPoint()).thenReturn(endPoint);
    if (emitStableDatabaseSemconv()) {
      when(coordinator.getBroadcastRpcAddress())
          .thenReturn(Optional.of(InetSocketAddress.createUnresolved("10.0.0.5", 9042)));
    }

    Attributes attributes = serverAttributes();

    if (emitStableDatabaseSemconv()) {
      assertThat(attributes.get(SERVER_ADDRESS)).isEqualTo("10.0.0.5");
      assertThat(attributes.get(SERVER_PORT)).isEqualTo(9042L);
    } else {
      assertProxyIsServer(attributes);
    }
  }

  @Test
  void sniEndPointOmitsServerAddressWhenServerNameIsHostId() {
    // In cloud deployments the driver builds the SNI server name from the node's host id, which is
    // not an address, so nothing is recorded rather than the host id. The frozen old conventions
    // still record the proxy returned by resolve().
    UUID hostId = UUID.fromString("2a1c1d5e-7b0e-4d3a-9a1f-2f5a6c8b0d31");
    SniEndPoint endPoint = new SniEndPoint(PROXY_ADDRESS, hostId.toString());
    when(coordinator.getEndPoint()).thenReturn(endPoint);
    if (emitStableDatabaseSemconv()) {
      when(coordinator.getBroadcastRpcAddress()).thenReturn(Optional.empty());
      when(coordinator.getHostId()).thenReturn(hostId);
    }

    Attributes attributes = serverAttributes();

    if (emitStableDatabaseSemconv()) {
      assertThat(attributes.get(SERVER_ADDRESS)).isNull();
      assertThat(attributes.get(SERVER_PORT)).isNull();
    } else {
      assertProxyIsServer(attributes);
    }
  }

  @Test
  void sniEndPointFallsBackToServerNameWhenItIsNotHostId() {
    SniEndPoint endPoint = new SniEndPoint(PROXY_ADDRESS, "node1.example.com");
    when(coordinator.getEndPoint()).thenReturn(endPoint);
    if (emitStableDatabaseSemconv()) {
      when(coordinator.getBroadcastRpcAddress()).thenReturn(Optional.empty());
      when(coordinator.getHostId())
          .thenReturn(UUID.fromString("2a1c1d5e-7b0e-4d3a-9a1f-2f5a6c8b0d31"));
    }

    Attributes attributes = serverAttributes();

    if (emitStableDatabaseSemconv()) {
      assertThat(attributes.get(SERVER_ADDRESS)).isEqualTo("node1.example.com");
      assertThat(attributes.get(SERVER_PORT)).isNull();
    } else {
      assertProxyIsServer(attributes);
    }
  }

  @Test
  void customEndPointUsesResolvedAddressForServer() {
    when(coordinator.getEndPoint()).thenReturn(customEndPoint);
    when(customEndPoint.resolve())
        .thenReturn(InetSocketAddress.createUnresolved("node.example.com", 9042));

    Attributes attributes = serverAttributes();

    assertThat(attributes.get(SERVER_ADDRESS)).isEqualTo("node.example.com");
    assertThat(attributes.get(SERVER_PORT)).isEqualTo(9042L);
  }

  @Test
  void networkPeerIsOmittedForSniEndPoint() {
    when(executionInfo.getCoordinator()).thenReturn(coordinator);
    SniEndPoint endPoint = new SniEndPoint(PROXY_ADDRESS, "host-id");
    when(coordinator.getEndPoint()).thenReturn(endPoint);

    CassandraSqlAttributesGetter getter = new CassandraSqlAttributesGetter();
    if (emitStableDatabaseSemconv()) {
      assertThat(getter.getNetworkPeerInetSocketAddress(null, executionInfo)).isNull();
    } else {
      // The old conventions are frozen and still record the proxy returned by resolve() as the
      // peer.
      InetSocketAddress peer = getter.getNetworkPeerInetSocketAddress(null, executionInfo);
      assertThat(peer).isNotNull();
      assertThat(peer.getAddress().isLoopbackAddress()).isTrue();
      assertThat(peer.getPort()).isEqualTo(29042);
    }
  }

  @Test
  void networkPeerIsResolvedAddressUnderCustomEndPoint() {
    when(executionInfo.getCoordinator()).thenReturn(coordinator);
    when(coordinator.getEndPoint()).thenReturn(customEndPoint);
    when(customEndPoint.resolve())
        .thenReturn(InetSocketAddress.createUnresolved("node.example.com", 9042));

    CassandraSqlAttributesGetter getter = new CassandraSqlAttributesGetter();
    InetSocketAddress peer = getter.getNetworkPeerInetSocketAddress(null, executionInfo);

    assertThat(peer).isNotNull();
    assertThat(peer.getHostString()).isEqualTo("node.example.com");
    assertThat(peer.getPort()).isEqualTo(9042);
  }

  @Test
  void networkPeerIsResolvedAddressUnderDefaultEndPoint() throws UnknownHostException {
    DefaultEndPoint endPoint = new DefaultEndPoint(resolved(9042));
    when(executionInfo.getCoordinator()).thenReturn(coordinator);
    when(coordinator.getEndPoint()).thenReturn(endPoint);

    CassandraSqlAttributesGetter getter = new CassandraSqlAttributesGetter();
    InetSocketAddress peer = getter.getNetworkPeerInetSocketAddress(null, executionInfo);

    assertThat(peer).isNotNull();
    assertThat(peer.getHostString()).isEqualTo("127.0.0.1");
    assertThat(peer.getPort()).isEqualTo(9042);
  }

  private Attributes serverAttributes() {
    AttributesBuilder builder = Attributes.builder();
    CassandraAttributesExtractor.updateServerAddressAndPort(builder, coordinator);
    return builder.build();
  }

  // PROXY_ADDRESS is unresolved, so resolve() turns it into the loopback address it names.
  private static void assertProxyIsServer(Attributes attributes) {
    assertThat(attributes.get(SERVER_ADDRESS)).isEqualTo("127.0.0.1");
    assertThat(attributes.get(SERVER_PORT)).isEqualTo(29042L);
  }

  // DefaultEndPoint requires a resolved address; build one from raw loopback bytes so getHostString
  // returns the literal without a lookup. Do not build an SNI proxy address this way, because
  // SniEndPoint.resolve() reads getHostName(), which reverse-resolves an address built from bytes.
  private static InetSocketAddress resolved(int port) throws UnknownHostException {
    return new InetSocketAddress(InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), port);
  }
}
