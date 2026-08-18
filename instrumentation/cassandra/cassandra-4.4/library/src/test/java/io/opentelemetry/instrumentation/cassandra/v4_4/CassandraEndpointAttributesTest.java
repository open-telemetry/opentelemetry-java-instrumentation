/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.cassandra.v4_4;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldDatabaseSemconv;
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

// The SNI (proxied) path cannot be exercised against the Cassandra test container, so this unit
// test covers the endpoint-to-attribute mapping directly.
@ExtendWith(MockitoExtension.class)
class CassandraEndpointAttributesTest {

  @Mock private ExecutionInfo executionInfo;
  @Mock private Node coordinator;
  @Mock private EndPoint customEndPoint;

  @Test
  void defaultEndPointUsesResolvedAddressForServer() throws UnknownHostException {
    DefaultEndPoint endPoint = new DefaultEndPoint(resolved(9042));
    when(coordinator.getEndPoint()).thenReturn(endPoint);

    AttributesBuilder builder = Attributes.builder();
    CassandraAttributesExtractor.updateServerAddressAndPort(builder, coordinator);
    Attributes attributes = builder.build();

    assertThat(attributes.get(SERVER_ADDRESS)).isEqualTo("127.0.0.1");
    assertThat(attributes.get(SERVER_PORT)).isEqualTo(9042L);
  }

  @Test
  void sniEndPointUsesBroadcastRpcAddressForServer() {
    // The proxy the client actually connects to differs from the node behind it. Under the stable
    // conventions the broadcast RPC address is recorded; under the frozen old conventions the proxy
    // read by reflection is recorded, so each mode pins a different value.
    SniEndPoint endPoint =
        new SniEndPoint(InetSocketAddress.createUnresolved("proxy.example.com", 29042), "host-id");
    when(coordinator.getEndPoint()).thenReturn(endPoint);
    if (emitStableDatabaseSemconv()) {
      when(coordinator.getBroadcastRpcAddress())
          .thenReturn(Optional.of(InetSocketAddress.createUnresolved("10.0.0.5", 9042)));
    }

    AttributesBuilder builder = Attributes.builder();
    CassandraAttributesExtractor.updateServerAddressAndPort(builder, coordinator);
    Attributes attributes = builder.build();

    if (emitStableDatabaseSemconv()) {
      assertThat(attributes.get(SERVER_ADDRESS)).isEqualTo("10.0.0.5");
      assertThat(attributes.get(SERVER_PORT)).isEqualTo(9042L);
    } else {
      assertThat(attributes.get(SERVER_ADDRESS)).isEqualTo("proxy.example.com");
      assertThat(attributes.get(SERVER_PORT)).isEqualTo(29042L);
    }
  }

  @Test
  void sniEndPointOmitsServerAddressWhenServerNameIsHostId() {
    // In cloud deployments the driver builds the SNI server name from the node's host id, which is
    // not an address, so nothing is recorded rather than the host id. The frozen old conventions
    // still record the proxy read by reflection.
    UUID hostId = UUID.fromString("2a1c1d5e-7b0e-4d3a-9a1f-2f5a6c8b0d31");
    SniEndPoint endPoint =
        new SniEndPoint(
            InetSocketAddress.createUnresolved("proxy.example.com", 29042), hostId.toString());
    when(coordinator.getEndPoint()).thenReturn(endPoint);
    if (emitStableDatabaseSemconv()) {
      when(coordinator.getBroadcastRpcAddress()).thenReturn(Optional.empty());
      when(coordinator.getHostId()).thenReturn(hostId);
    }

    AttributesBuilder builder = Attributes.builder();
    CassandraAttributesExtractor.updateServerAddressAndPort(builder, coordinator);
    Attributes attributes = builder.build();

    if (emitStableDatabaseSemconv()) {
      assertThat(attributes.get(SERVER_ADDRESS)).isNull();
      assertThat(attributes.get(SERVER_PORT)).isNull();
    } else {
      assertThat(attributes.get(SERVER_ADDRESS)).isEqualTo("proxy.example.com");
      assertThat(attributes.get(SERVER_PORT)).isEqualTo(29042L);
    }
  }

  @Test
  void sniEndPointFallsBackToServerNameWhenItIsNotHostId() {
    SniEndPoint endPoint =
        new SniEndPoint(
            InetSocketAddress.createUnresolved("proxy.example.com", 29042), "node1.example.com");
    when(coordinator.getEndPoint()).thenReturn(endPoint);
    if (emitStableDatabaseSemconv()) {
      when(coordinator.getBroadcastRpcAddress()).thenReturn(Optional.empty());
      when(coordinator.getHostId())
          .thenReturn(UUID.fromString("2a1c1d5e-7b0e-4d3a-9a1f-2f5a6c8b0d31"));
    }

    AttributesBuilder builder = Attributes.builder();
    CassandraAttributesExtractor.updateServerAddressAndPort(builder, coordinator);
    Attributes attributes = builder.build();

    if (emitStableDatabaseSemconv()) {
      assertThat(attributes.get(SERVER_ADDRESS)).isEqualTo("node1.example.com");
      assertThat(attributes.get(SERVER_PORT)).isNull();
    } else {
      assertThat(attributes.get(SERVER_ADDRESS)).isEqualTo("proxy.example.com");
      assertThat(attributes.get(SERVER_PORT)).isEqualTo(29042L);
    }
  }

  @Test
  void customEndPointUsesResolvedAddressForServer() {
    when(coordinator.getEndPoint()).thenReturn(customEndPoint);
    when(customEndPoint.resolve())
        .thenReturn(InetSocketAddress.createUnresolved("node.example.com", 9042));

    AttributesBuilder builder = Attributes.builder();
    CassandraAttributesExtractor.updateServerAddressAndPort(builder, coordinator);
    Attributes attributes = builder.build();

    assertThat(attributes.get(SERVER_ADDRESS)).isEqualTo("node.example.com");
    assertThat(attributes.get(SERVER_PORT)).isEqualTo(9042L);
  }

  @Test
  void networkPeerPreservesProxyUnderOldSemconvAndIsOmittedUnderStableSemconv() {
    SniEndPoint endPoint =
        new SniEndPoint(InetSocketAddress.createUnresolved("proxy.example.com", 29042), "host-id");
    when(executionInfo.getCoordinator()).thenReturn(coordinator);
    when(coordinator.getEndPoint()).thenReturn(endPoint);

    CassandraSqlAttributesGetter getter = new CassandraSqlAttributesGetter();
    InetSocketAddress peer = getter.getNetworkPeerInetSocketAddress(null, executionInfo);

    if (emitStableDatabaseSemconv()) {
      assertThat(peer).isNull();
    }
    if (emitOldDatabaseSemconv()) {
      assertThat(peer).isNotNull();
      assertThat(peer.getHostString()).isEqualTo("proxy.example.com");
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

  // DefaultEndPoint requires a resolved address; build one from raw loopback bytes so getHostString
  // stays deterministic and no hostname is ever looked up.
  private static InetSocketAddress resolved(int port) throws UnknownHostException {
    return new InetSocketAddress(InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), port);
  }
}
