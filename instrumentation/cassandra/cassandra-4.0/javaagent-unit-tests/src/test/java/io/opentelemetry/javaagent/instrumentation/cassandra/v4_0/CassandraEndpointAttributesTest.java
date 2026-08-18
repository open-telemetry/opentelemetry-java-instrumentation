/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v4_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.datastax.oss.driver.api.core.cql.ExecutionInfo;
import com.datastax.oss.driver.api.core.metadata.EndPoint;
import com.datastax.oss.driver.api.core.metadata.Node;
import com.datastax.oss.driver.internal.core.metadata.DefaultEndPoint;
import com.datastax.oss.driver.internal.core.metadata.SniEndPoint;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// The proxied (SNI) path cannot be exercised against the Cassandra test container, so this unit
// test covers the endpoint-to-address mapping directly.
@ExtendWith(MockitoExtension.class)
class CassandraEndpointAttributesTest {

  @Mock private ExecutionInfo executionInfo;
  @Mock private Node coordinator;
  @Mock private EndPoint customEndPoint;

  @Test
  void defaultEndPointUsesResolvedAddressForServer() throws UnknownHostException {
    DefaultEndPoint endPoint = new DefaultEndPoint(resolved(9042));
    when(coordinator.getEndPoint()).thenReturn(endPoint);

    InetSocketAddress server = CassandraAttributesExtractor.getServerAddress(coordinator);

    assertThat(server).isNotNull();
    assertThat(server.getHostString()).isEqualTo("127.0.0.1");
    assertThat(server.getPort()).isEqualTo(9042);
  }

  @Test
  void sniEndPointUsesBroadcastRpcAddressForServer() throws UnknownHostException {
    // The proxy the client actually connects to differs from the node behind it. Under the stable
    // conventions the broadcast RPC address is recorded; under the frozen old conventions the proxy
    // returned by resolve() is recorded, so each mode pins a different value.
    SniEndPoint endPoint = new SniEndPoint(resolved(29042), "host-id");
    when(coordinator.getEndPoint()).thenReturn(endPoint);
    if (emitStableDatabaseSemconv()) {
      when(coordinator.getBroadcastRpcAddress())
          .thenReturn(Optional.of(InetSocketAddress.createUnresolved("10.0.0.5", 9042)));
    }

    InetSocketAddress server = CassandraAttributesExtractor.getServerAddress(coordinator);

    assertThat(server).isNotNull();
    if (emitStableDatabaseSemconv()) {
      assertThat(server.getHostString()).isEqualTo("10.0.0.5");
      assertThat(server.getPort()).isEqualTo(9042);
    } else {
      assertThat(server.getAddress().isLoopbackAddress()).isTrue();
      assertThat(server.getPort()).isEqualTo(29042);
    }
  }

  @Test
  void sniEndPointOmitsServerAddressWithoutBroadcastRpcAddress() throws UnknownHostException {
    SniEndPoint endPoint = new SniEndPoint(resolved(29042), "host-id");
    when(coordinator.getEndPoint()).thenReturn(endPoint);
    if (emitStableDatabaseSemconv()) {
      when(coordinator.getBroadcastRpcAddress()).thenReturn(Optional.empty());
      assertThat(CassandraAttributesExtractor.getServerAddress(coordinator)).isNull();
    } else {
      // The old conventions are frozen and still record the proxy returned by resolve().
      InetSocketAddress server = CassandraAttributesExtractor.getServerAddress(coordinator);
      assertThat(server).isNotNull();
      assertThat(server.getAddress().isLoopbackAddress()).isTrue();
      assertThat(server.getPort()).isEqualTo(29042);
    }
  }

  @Test
  void customEndPointUsesResolvedAddressForServer() {
    when(coordinator.getEndPoint()).thenReturn(customEndPoint);
    when(customEndPoint.resolve())
        .thenReturn(InetSocketAddress.createUnresolved("node.example.com", 9042));

    InetSocketAddress server = CassandraAttributesExtractor.getServerAddress(coordinator);

    assertThat(server).isNotNull();
    assertThat(server.getHostString()).isEqualTo("node.example.com");
    assertThat(server.getPort()).isEqualTo(9042);
  }

  @Test
  void networkPeerIsOmittedForSniEndPoint() throws UnknownHostException {
    when(executionInfo.getCoordinator()).thenReturn(coordinator);
    SniEndPoint endPoint = new SniEndPoint(resolved(29042), "host-id");
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

  // DefaultEndPoint requires a resolved address; build one from raw loopback bytes so getHostString
  // stays deterministic and no hostname is ever looked up.
  private static InetSocketAddress resolved(int port) throws UnknownHostException {
    return new InetSocketAddress(InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), port);
  }
}
