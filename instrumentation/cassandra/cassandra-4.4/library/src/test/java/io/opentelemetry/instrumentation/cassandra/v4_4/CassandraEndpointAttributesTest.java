/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.cassandra.v4_4;

import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.datastax.oss.driver.api.core.cql.ExecutionInfo;
import com.datastax.oss.driver.api.core.metadata.Node;
import com.datastax.oss.driver.internal.core.metadata.DefaultEndPoint;
import com.datastax.oss.driver.internal.core.metadata.SniEndPoint;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Optional;
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
    // The proxy the client actually connects to differs from the node behind it, so asserting the
    // broadcast RPC values fails against the old behavior, which recorded the proxy in server.*.
    SniEndPoint endPoint =
        new SniEndPoint(InetSocketAddress.createUnresolved("proxy.example.com", 29042), "host-id");
    when(coordinator.getEndPoint()).thenReturn(endPoint);
    when(coordinator.getBroadcastRpcAddress())
        .thenReturn(Optional.of(InetSocketAddress.createUnresolved("10.0.0.5", 9042)));

    AttributesBuilder builder = Attributes.builder();
    CassandraAttributesExtractor.updateServerAddressAndPort(builder, coordinator);
    Attributes attributes = builder.build();

    assertThat(attributes.get(SERVER_ADDRESS)).isEqualTo("10.0.0.5");
    assertThat(attributes.get(SERVER_PORT)).isEqualTo(9042L);
  }

  @Test
  void sniEndPointFallsBackToServerNameWhenNoRpcAddress() {
    SniEndPoint endPoint =
        new SniEndPoint(InetSocketAddress.createUnresolved("proxy.example.com", 29042), "host-id");
    when(coordinator.getEndPoint()).thenReturn(endPoint);
    when(coordinator.getBroadcastRpcAddress()).thenReturn(Optional.empty());

    AttributesBuilder builder = Attributes.builder();
    CassandraAttributesExtractor.updateServerAddressAndPort(builder, coordinator);
    Attributes attributes = builder.build();

    assertThat(attributes.get(SERVER_ADDRESS)).isEqualTo("host-id");
    assertThat(attributes.get(SERVER_PORT)).isNull();
  }

  @Test
  void networkPeerIsOmittedUnderSni() {
    SniEndPoint endPoint =
        new SniEndPoint(InetSocketAddress.createUnresolved("proxy.example.com", 29042), "host-id");
    when(executionInfo.getCoordinator()).thenReturn(coordinator);
    when(coordinator.getEndPoint()).thenReturn(endPoint);

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
