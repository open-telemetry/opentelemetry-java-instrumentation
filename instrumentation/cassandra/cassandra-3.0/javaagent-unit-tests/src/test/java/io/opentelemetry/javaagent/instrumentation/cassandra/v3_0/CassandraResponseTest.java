/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v3_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.javaagent.instrumentation.cassandra.v3_0.TestEndPoints.PROXY_ADDRESS;
import static io.opentelemetry.javaagent.instrumentation.cassandra.v3_0.TestEndPoints.address;
import static io.opentelemetry.javaagent.instrumentation.cassandra.v3_0.TestEndPoints.plainEndPoint;
import static io.opentelemetry.javaagent.instrumentation.cassandra.v3_0.TestEndPoints.sniEndPoint;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.datastax.driver.core.ConsistencyLevel;
import com.datastax.driver.core.ExecutionInfo;
import com.datastax.driver.core.Host;
import com.datastax.driver.core.exceptions.UnavailableException;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// The SNI (proxied) path cannot be exercised against the Cassandra test container, because no test
// container image runs a proxied deployment, so this unit test covers the endpoint-to-address
// mapping directly. CassandraResponse holds what the instrumentation records: the server address
// becomes server.address and server.port, the SNI server name becomes server.address on its own,
// and the peer address becomes network.peer.*. Under the frozen old database semantic conventions
// the proxy is still recorded, so every test that reaches the SNI branch pins both modes.
//
// Host.getSocketAddress() is deprecated in driver 3.11.5, but the instrumentation supports drivers
// back to 3.0 where it is not, and the frozen old conventions still record it.
@SuppressWarnings("deprecation")
@ExtendWith(MockitoExtension.class)
class CassandraResponseTest {

  private static final byte[] NODE_IP = {10, 0, 0, 5};
  private static final byte[] LOOPBACK_IP = {127, 0, 0, 1};
  private static final byte[] LOOPBACK_IPV6 = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1};

  @Mock private ExecutionInfo executionInfo;
  @Mock private Host coordinator;

  @Test
  void plainEndPointRecordsSocketAddressAsServerAndPeer() throws UnknownHostException {
    InetSocketAddress socketAddress = address(LOOPBACK_IP, 9042);
    when(executionInfo.getQueriedHost()).thenReturn(coordinator);
    when(coordinator.getSocketAddress()).thenReturn(socketAddress);
    if (emitStableDatabaseSemconv()) {
      when(coordinator.getEndPoint()).thenReturn(plainEndPoint(socketAddress));
    }

    CassandraResponse response = CassandraResponse.create(executionInfo);

    assertThat(response.getServerAddress()).isEqualTo(socketAddress);
    assertThat(peerAddress(response)).isEqualTo(socketAddress);
  }

  @Test
  void ipv6PlainEndPointRecordsSocketAddressAsServerAndPeer() throws UnknownHostException {
    InetSocketAddress socketAddress = address(LOOPBACK_IPV6, 9042);
    when(executionInfo.getQueriedHost()).thenReturn(coordinator);
    when(coordinator.getSocketAddress()).thenReturn(socketAddress);
    if (emitStableDatabaseSemconv()) {
      when(coordinator.getEndPoint()).thenReturn(plainEndPoint(socketAddress));
    }

    CassandraResponse response = CassandraResponse.create(executionInfo);

    // an address stays a bare address, without the brackets a host:port pair would need
    assertThat(response.getServerAddress()).isNotNull();
    assertThat(response.getServerAddress().getHostString()).isEqualTo("0:0:0:0:0:0:0:1");
    assertThat(peerAddress(response)).isEqualTo(socketAddress);
  }

  @Test
  void sniEndPointRecordsBroadcastRpcAddressAsServerAndNoPeer() throws UnknownHostException {
    // Under the stable conventions the coordinator's own broadcast rpc address is recorded and the
    // peer is left unset, because the proxy socket is only reachable through a resolving call. The
    // old conventions never look at the endpoint and keep recording the proxy socket address.
    InetSocketAddress broadcastRpcAddress = address(NODE_IP, 9042);
    when(executionInfo.getQueriedHost()).thenReturn(coordinator);
    if (emitStableDatabaseSemconv()) {
      when(coordinator.getEndPoint()).thenReturn(sniEndPoint());
      when(coordinator.getBroadcastRpcAddress()).thenReturn(broadcastRpcAddress);
    } else {
      when(coordinator.getSocketAddress()).thenReturn(PROXY_ADDRESS);
    }

    CassandraResponse response = CassandraResponse.create(executionInfo);

    if (emitStableDatabaseSemconv()) {
      assertThat(response.getServerAddress()).isEqualTo(broadcastRpcAddress);
      assertThat(peerAddress(response)).isNull();
    } else {
      assertServerAndPeerAreProxy(response);
    }
  }

  @Test
  void sniEndPointOmitsServerAddressWhenServerNameIsHostId() {
    // Cloud deployments name each node by its host id, which is already recorded as
    // cassandra.coordinator.id, so there is nothing address-like left to record.
    UUID hostId = UUID.randomUUID();
    when(executionInfo.getQueriedHost()).thenReturn(coordinator);
    if (emitStableDatabaseSemconv()) {
      when(coordinator.getEndPoint()).thenReturn(sniEndPoint(hostId.toString()));
      when(coordinator.getBroadcastRpcAddress()).thenReturn(null);
      when(coordinator.getHostId()).thenReturn(hostId);
    } else {
      when(coordinator.getSocketAddress()).thenReturn(PROXY_ADDRESS);
    }

    CassandraResponse response = CassandraResponse.create(executionInfo);

    if (emitStableDatabaseSemconv()) {
      assertThat(response.getServerAddress()).isNull();
      assertThat(response.getServerName()).isNull();
      assertThat(peerAddress(response)).isNull();
    } else {
      assertServerAndPeerAreProxy(response);
    }
  }

  @Test
  void sniEndPointFallsBackToServerNameWhenItIsNotHostId() {
    // A custom SNI proxy may name nodes by host name, which is a usable server address, though it
    // carries no port.
    when(executionInfo.getQueriedHost()).thenReturn(coordinator);
    if (emitStableDatabaseSemconv()) {
      when(coordinator.getEndPoint()).thenReturn(sniEndPoint("node1.example.com"));
      when(coordinator.getBroadcastRpcAddress()).thenReturn(null);
      when(coordinator.getHostId()).thenReturn(UUID.randomUUID());
    } else {
      when(coordinator.getSocketAddress()).thenReturn(PROXY_ADDRESS);
    }

    CassandraResponse response = CassandraResponse.create(executionInfo);

    if (emitStableDatabaseSemconv()) {
      assertThat(response.getServerAddress()).isNull();
      assertThat(response.getServerName()).isEqualTo("node1.example.com");
      assertThat(peerAddress(response)).isNull();
    } else {
      assertServerAndPeerAreProxy(response);
    }
  }

  @Test
  void plainEndPointExceptionRecordsItsAddressAsServerAndPeer() throws UnknownHostException {
    InetSocketAddress socketAddress = address(LOOPBACK_IP, 9042);
    UnavailableException exception =
        new UnavailableException(plainEndPoint(socketAddress), ConsistencyLevel.ONE, 1, 0);

    CassandraResponse response = CassandraResponse.create(exception);

    assertThat(response).isNotNull();
    assertThat(response.getServerAddress()).isEqualTo(socketAddress);
    assertThat(peerAddress(response)).isEqualTo(socketAddress);
  }

  @Test
  void sniEndPointExceptionRecordsNothing() {
    // The exception knows only the proxy endpoint, and reading its address resolves that endpoint,
    // so the stable conventions record neither address. The old conventions still record the proxy.
    UnavailableException exception =
        new UnavailableException(sniEndPoint(), ConsistencyLevel.ONE, 1, 0);

    CassandraResponse response = CassandraResponse.create(exception);

    assertThat(response).isNotNull();
    if (emitStableDatabaseSemconv()) {
      assertThat(response.getServerAddress()).isNull();
      assertThat(peerAddress(response)).isNull();
    } else {
      assertServerAndPeerAreProxy(response);
    }
  }

  @Test
  void missingCoordinatorRecordsNothing() {
    when(executionInfo.getQueriedHost()).thenReturn(null);

    CassandraResponse response = CassandraResponse.create(executionInfo);

    assertThat(response.getServerAddress()).isNull();
    assertThat(peerAddress(response)).isNull();
  }

  private static void assertServerAndPeerAreProxy(CassandraResponse response) {
    InetSocketAddress server = response.getServerAddress();
    assertThat(server).isNotNull();
    assertThat(server.getHostString()).isEqualTo(PROXY_ADDRESS.getHostString());
    assertThat(server.getPort()).isEqualTo(PROXY_ADDRESS.getPort());

    InetSocketAddress peer = peerAddress(response);
    assertThat(peer).isNotNull();
    assertThat(peer.getHostString()).isEqualTo(PROXY_ADDRESS.getHostString());
    assertThat(peer.getPort()).isEqualTo(PROXY_ADDRESS.getPort());
  }

  // network.peer.* is read through the attributes getter, so assert through it rather than reading
  // the response directly.
  private static InetSocketAddress peerAddress(CassandraResponse response) {
    return new CassandraSqlAttributesGetter().getNetworkPeerInetSocketAddress(null, response);
  }
}
