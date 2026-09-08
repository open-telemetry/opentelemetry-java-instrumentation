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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// The Cassandra test container cannot exercise SNI, so these tests cover endpoint mapping directly.
// Host.getSocketAddress() is deprecated in driver 3.11.5, but the instrumentation supports drivers
// back to 3.0, where it is not deprecated.
@SuppressWarnings("deprecation")
@ExtendWith(MockitoExtension.class)
class CassandraResponseTest {

  private static final byte[] LOOPBACK_IP = {127, 0, 0, 1};
  private static final byte[] LOOPBACK_IPV6 = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1};

  @Mock private ExecutionInfo executionInfo;
  @Mock private Host coordinator;

  @Test
  void plainEndPointRecordsSocketAddressAsPeer() throws UnknownHostException {
    InetSocketAddress socketAddress = address(LOOPBACK_IP, 9042);
    when(executionInfo.getQueriedHost()).thenReturn(coordinator);
    when(coordinator.getSocketAddress()).thenReturn(socketAddress);
    if (emitStableDatabaseSemconv()) {
      when(coordinator.getEndPoint()).thenReturn(plainEndPoint(socketAddress));
    }

    CassandraResponse response = CassandraResponse.create(executionInfo);

    assertThat(peerAddress(response)).isEqualTo(socketAddress);
  }

  @Test
  void ipv6PlainEndPointRecordsSocketAddressAsPeer() throws UnknownHostException {
    InetSocketAddress socketAddress = address(LOOPBACK_IPV6, 9042);
    when(executionInfo.getQueriedHost()).thenReturn(coordinator);
    when(coordinator.getSocketAddress()).thenReturn(socketAddress);
    if (emitStableDatabaseSemconv()) {
      when(coordinator.getEndPoint()).thenReturn(plainEndPoint(socketAddress));
    }

    CassandraResponse response = CassandraResponse.create(executionInfo);

    assertThat(peerAddress(response)).isEqualTo(socketAddress);
  }

  @Test
  void sniEndPointDoesNotResolvePeerInStableMode() {
    when(executionInfo.getQueriedHost()).thenReturn(coordinator);
    if (emitStableDatabaseSemconv()) {
      when(coordinator.getEndPoint()).thenReturn(sniEndPoint());
    } else {
      when(coordinator.getSocketAddress()).thenReturn(PROXY_ADDRESS);
    }

    CassandraResponse response = CassandraResponse.create(executionInfo);

    if (emitStableDatabaseSemconv()) {
      assertThat(peerAddress(response)).isNull();
    } else {
      assertPeerIsProxy(response);
    }
  }

  @Test
  void plainEndPointExceptionRecordsItsAddressAsPeer() throws UnknownHostException {
    InetSocketAddress socketAddress = address(LOOPBACK_IP, 9042);
    UnavailableException exception =
        new UnavailableException(plainEndPoint(socketAddress), ConsistencyLevel.ONE, 1, 0);

    CassandraResponse response = CassandraResponse.create(exception);

    assertThat(response).isNotNull();
    assertThat(peerAddress(response)).isEqualTo(socketAddress);
  }

  @Test
  void sniEndPointExceptionDoesNotResolvePeerInStableMode() {
    UnavailableException exception =
        new UnavailableException(sniEndPoint(), ConsistencyLevel.ONE, 1, 0);

    CassandraResponse response = CassandraResponse.create(exception);

    assertThat(response).isNotNull();
    if (emitStableDatabaseSemconv()) {
      assertThat(peerAddress(response)).isNull();
    } else {
      assertPeerIsProxy(response);
    }
  }

  @Test
  void missingCoordinatorRecordsNothing() {
    when(executionInfo.getQueriedHost()).thenReturn(null);

    CassandraResponse response = CassandraResponse.create(executionInfo);

    assertThat(peerAddress(response)).isNull();
  }

  private static void assertPeerIsProxy(CassandraResponse response) {
    InetSocketAddress peer = peerAddress(response);
    assertThat(peer).isNotNull();
    assertThat(peer.getHostString()).isEqualTo(PROXY_ADDRESS.getHostString());
    assertThat(peer.getPort()).isEqualTo(PROXY_ADDRESS.getPort());
  }

  private static InetSocketAddress peerAddress(CassandraResponse response) {
    return new CassandraSqlAttributesGetter().getNetworkPeerInetSocketAddress(null, response);
  }
}
