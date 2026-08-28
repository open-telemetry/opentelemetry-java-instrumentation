/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v3_0;

import static io.opentelemetry.javaagent.instrumentation.cassandra.v3_0.TestEndPoints.address;
import static io.opentelemetry.javaagent.instrumentation.cassandra.v3_0.TestEndPoints.plainEndPoint;
import static io.opentelemetry.javaagent.instrumentation.cassandra.v3_0.TestEndPoints.sniEndPoint;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.datastax.driver.core.ConsistencyLevel;
import com.datastax.driver.core.Host;
import com.datastax.driver.core.exceptions.UnavailableException;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// Host.getHostId() was added in driver 3.3.1. The other reflected APIs were added in 3.8.0.
// These tests use 3.11.5; the javaagent module tests use 3.2.0, where none of them exist.
@ExtendWith(MockitoExtension.class)
class CassandraEndPointsTest {

  private static final byte[] LOOPBACK_IP = {127, 0, 0, 1};

  @Mock private Host coordinator;

  @Test
  void recognizesSniEndPointOnHost() {
    when(coordinator.getEndPoint()).thenReturn(sniEndPoint());

    assertThat(CassandraEndPoints.isSniEndPoint(coordinator)).isTrue();
  }

  @Test
  void doesNotRecognizePlainEndPointOnHost() throws UnknownHostException {
    when(coordinator.getEndPoint()).thenReturn(plainEndPoint(address(LOOPBACK_IP, 9042)));

    assertThat(CassandraEndPoints.isSniEndPoint(coordinator)).isFalse();
  }

  @Test
  void recognizesSniEndPointOnException() {
    UnavailableException exception =
        new UnavailableException(sniEndPoint(), ConsistencyLevel.ONE, 1, 0);

    assertThat(CassandraEndPoints.isSniEndPoint(exception)).isTrue();
  }

  @Test
  void doesNotRecognizePlainEndPointOnException() throws UnknownHostException {
    UnavailableException exception =
        new UnavailableException(
            plainEndPoint(address(LOOPBACK_IP, 9042)), ConsistencyLevel.ONE, 1, 0);

    assertThat(CassandraEndPoints.isSniEndPoint(exception)).isFalse();
  }

  @Test
  void readsBroadcastRpcAddress() throws UnknownHostException {
    InetSocketAddress broadcastRpcAddress = address(new byte[] {10, 0, 0, 5}, 9042);
    when(coordinator.getBroadcastRpcAddress()).thenReturn(broadcastRpcAddress);

    assertThat(CassandraEndPoints.getBroadcastRpcAddress(coordinator))
        .isEqualTo(broadcastRpcAddress);
  }

  @Test
  void readsSniServerName() {
    when(coordinator.getEndPoint()).thenReturn(sniEndPoint("node1.example.com"));

    assertThat(CassandraEndPoints.getSniServerName(coordinator)).isEqualTo("node1.example.com");
  }

  @Test
  void readsNoSniServerNameFromPlainEndPoint() throws UnknownHostException {
    when(coordinator.getEndPoint()).thenReturn(plainEndPoint(address(LOOPBACK_IP, 9042)));

    assertThat(CassandraEndPoints.getSniServerName(coordinator)).isNull();
  }

  @Test
  void readsHostId() {
    UUID hostId = UUID.randomUUID();
    when(coordinator.getHostId()).thenReturn(hostId);

    assertThat(CassandraEndPoints.getHostId(coordinator)).isEqualTo(hostId.toString());
  }
}
