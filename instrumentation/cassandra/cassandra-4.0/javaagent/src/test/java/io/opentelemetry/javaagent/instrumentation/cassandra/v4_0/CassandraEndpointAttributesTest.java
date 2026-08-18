/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v4_0;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.datastax.oss.driver.api.core.cql.ExecutionInfo;
import com.datastax.oss.driver.api.core.metadata.EndPoint;
import com.datastax.oss.driver.api.core.metadata.Node;
import com.datastax.oss.driver.internal.core.metadata.DefaultEndPoint;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// The proxied (SNI) path cannot be exercised against the Cassandra test container, so this unit
// test covers the endpoint-to-address mapping directly. Driver 4.0 to 4.2 have no SniEndPoint, so
// the proxied endpoint is a plain mock of the EndPoint interface.
@ExtendWith(MockitoExtension.class)
class CassandraEndpointAttributesTest {

  @Mock private ExecutionInfo executionInfo;
  @Mock private Node coordinator;
  @Mock private EndPoint proxyEndPoint;

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
  void proxiedEndPointUsesBroadcastRpcAddressForServer() {
    // The proxy the client actually connects to differs from the node behind it, so asserting the
    // broadcast rpc values fails against the old behavior, which recorded the proxy in server.*.
    when(coordinator.getEndPoint()).thenReturn(proxyEndPoint);
    when(coordinator.getBroadcastRpcAddress())
        .thenReturn(Optional.of(InetSocketAddress.createUnresolved("10.0.0.5", 9042)));

    InetSocketAddress server = CassandraAttributesExtractor.getServerAddress(coordinator);

    assertThat(server).isNotNull();
    assertThat(server.getHostString()).isEqualTo("10.0.0.5");
    assertThat(server.getPort()).isEqualTo(9042);
  }

  @Test
  void proxiedEndPointOmitsServerAddressWithoutBroadcastRpcAddress() {
    when(coordinator.getEndPoint()).thenReturn(proxyEndPoint);
    when(coordinator.getBroadcastRpcAddress()).thenReturn(Optional.empty());

    assertThat(CassandraAttributesExtractor.getServerAddress(coordinator)).isNull();
  }

  @Test
  void networkPeerIsOmittedForProxiedEndPoint() {
    when(executionInfo.getCoordinator()).thenReturn(coordinator);
    when(coordinator.getEndPoint()).thenReturn(proxyEndPoint);

    CassandraSqlAttributesGetter getter = new CassandraSqlAttributesGetter();
    InetSocketAddress peer = getter.getNetworkPeerInetSocketAddress(null, executionInfo);

    assertThat(peer).isNull();
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
