/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.cassandra.v4_4;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.datastax.oss.driver.api.core.cql.ExecutionInfo;
import com.datastax.oss.driver.api.core.metadata.Node;
import com.datastax.oss.driver.api.core.session.Session;
import com.datastax.oss.driver.internal.core.metadata.DefaultEndPoint;
import com.datastax.oss.driver.internal.core.metadata.SniEndPoint;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// Neither a proxied (SNI) deployment nor a multi node cluster can be exercised against the
// Cassandra test container, so this unit test covers the endpoint-to-attribute mapping directly.
// Under the frozen old database semantic conventions the coordinator is still recorded, so every
// test that changes under the stable conventions pins both modes.
@ExtendWith(MockitoExtension.class)
class CassandraEndpointAttributesTest {

  private static final InetSocketAddress PROXY_ADDRESS =
      InetSocketAddress.createUnresolved("proxy.example.com", 29042);

  @Mock private ExecutionInfo executionInfo;
  @Mock private Node coordinator;
  @Mock private Session session;

  @Test
  void unconfiguredSessionUsesTheCoordinatorAddress() throws UnknownHostException {
    when(coordinator.getEndPoint()).thenReturn(new DefaultEndPoint(resolved(9042)));

    Attributes attributes = serverAttributes(null);

    assertThat(attributes.get(SERVER_ADDRESS)).isEqualTo("127.0.0.1");
    assertThat(attributes.get(SERVER_PORT)).isEqualTo(9042L);
  }

  @Test
  void singleContactPointCarriesItsPort() throws UnknownHostException {
    when(coordinator.getEndPoint()).thenReturn(new DefaultEndPoint(resolved(9042)));

    Attributes attributes = serverAttributes(target(singletonList("cassandra.example.com:9042")));

    if (emitStableDatabaseSemconv()) {
      assertThat(attributes.get(SERVER_ADDRESS)).isEqualTo("cassandra.example.com");
      assertThat(attributes.get(SERVER_PORT)).isEqualTo(9042L);
    } else {
      assertCoordinatorIsServer(attributes);
    }
  }

  @Test
  void severalContactPointsAreOneTargetWithoutAPort() throws UnknownHostException {
    when(coordinator.getEndPoint()).thenReturn(new DefaultEndPoint(resolved(9042)));

    Attributes attributes =
        serverAttributes(target(asList("node1.example.com:9042", "node2.example.com:9042")));

    if (emitStableDatabaseSemconv()) {
      assertThat(attributes.get(SERVER_ADDRESS))
          .isEqualTo("node1.example.com:9042,node2.example.com:9042");
      assertThat(attributes.get(SERVER_PORT)).isNull();
    } else {
      assertCoordinatorIsServer(attributes);
    }
  }

  @Test
  void sniEndPointIgnoresTheConfiguredTargetAndUsesBroadcastRpcAddress() {
    // A proxied session reaches its nodes through an intermediary, so the node behind the proxy
    // wins over whatever the session names as its contact points.
    when(coordinator.getEndPoint()).thenReturn(new SniEndPoint(PROXY_ADDRESS, "host-id"));
    if (emitStableDatabaseSemconv()) {
      when(coordinator.getBroadcastRpcAddress())
          .thenReturn(Optional.of(InetSocketAddress.createUnresolved("10.0.0.5", 9042)));
    }

    Attributes attributes = serverAttributes(target(singletonList("proxy.example.com:29042")));

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
    // not an address, so nothing is recorded rather than the host id.
    UUID hostId = UUID.fromString("2a1c1d5e-7b0e-4d3a-9a1f-2f5a6c8b0d31");
    when(coordinator.getEndPoint()).thenReturn(new SniEndPoint(PROXY_ADDRESS, hostId.toString()));
    if (emitStableDatabaseSemconv()) {
      when(coordinator.getBroadcastRpcAddress()).thenReturn(Optional.empty());
      when(coordinator.getHostId()).thenReturn(hostId);
    }

    Attributes attributes = serverAttributes(null);

    if (emitStableDatabaseSemconv()) {
      assertThat(attributes.get(SERVER_ADDRESS)).isNull();
      assertThat(attributes.get(SERVER_PORT)).isNull();
    } else {
      assertProxyIsServer(attributes);
    }
  }

  @Test
  void sniEndPointFallsBackToServerNameWhenItIsNotHostId() {
    when(coordinator.getEndPoint()).thenReturn(new SniEndPoint(PROXY_ADDRESS, "node1.example.com"));
    if (emitStableDatabaseSemconv()) {
      when(coordinator.getBroadcastRpcAddress()).thenReturn(Optional.empty());
      when(coordinator.getHostId())
          .thenReturn(UUID.fromString("2a1c1d5e-7b0e-4d3a-9a1f-2f5a6c8b0d31"));
    }

    Attributes attributes = serverAttributes(null);

    if (emitStableDatabaseSemconv()) {
      assertThat(attributes.get(SERVER_ADDRESS)).isEqualTo("node1.example.com");
      assertThat(attributes.get(SERVER_PORT)).isNull();
    } else {
      assertProxyIsServer(attributes);
    }
  }

  @Test
  void networkPeerIsOmittedUnderSni() {
    when(executionInfo.getCoordinator()).thenReturn(coordinator);
    when(coordinator.getEndPoint()).thenReturn(new SniEndPoint(PROXY_ADDRESS, "host-id"));

    CassandraSqlAttributesGetter getter = new CassandraSqlAttributesGetter();

    assertThat(getter.getNetworkPeerInetSocketAddress(null, executionInfo)).isNull();
  }

  @Test
  void networkPeerIsTheCoordinatorSocketEvenWhenTheSessionNamesSeveralContactPoints()
      throws UnknownHostException {
    // network.peer.* describes the connection the request actually used, so the configured target
    // never reaches it.
    when(executionInfo.getCoordinator()).thenReturn(coordinator);
    when(coordinator.getEndPoint()).thenReturn(new DefaultEndPoint(resolved(9042)));

    CassandraSqlAttributesGetter getter = new CassandraSqlAttributesGetter();
    InetSocketAddress peer = getter.getNetworkPeerInetSocketAddress(null, executionInfo);

    assertThat(peer).isNotNull();
    assertThat(peer.getHostString()).isEqualTo("127.0.0.1");
    assertThat(peer.getPort()).isEqualTo(9042);
  }

  private Attributes serverAttributes(@Nullable CassandraServerTarget serverTarget) {
    AttributesBuilder builder = Attributes.builder();
    CassandraAttributesExtractor.updateServerAddressAndPort(
        builder, CassandraRequest.create(session, serverTarget, "SELECT 1"), coordinator);
    return builder.build();
  }

  @Nullable
  private static CassandraServerTarget target(List<String> contactPoints) {
    return CassandraServerTarget.of(contactPoints);
  }

  private static void assertCoordinatorIsServer(Attributes attributes) {
    assertThat(attributes.get(SERVER_ADDRESS)).isEqualTo("127.0.0.1");
    assertThat(attributes.get(SERVER_PORT)).isEqualTo(9042L);
  }

  private static void assertProxyIsServer(Attributes attributes) {
    assertThat(attributes.get(SERVER_ADDRESS)).isEqualTo("proxy.example.com");
    assertThat(attributes.get(SERVER_PORT)).isEqualTo(29042L);
  }

  // DefaultEndPoint requires a resolved address; build one from raw loopback bytes so getHostString
  // stays deterministic and no hostname is ever looked up.
  private static InetSocketAddress resolved(int port) throws UnknownHostException {
    return new InetSocketAddress(InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), port);
  }
}
