/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v4_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.datastax.oss.driver.api.core.metadata.EndPoint;
import com.datastax.oss.driver.api.core.metadata.Metadata;
import com.datastax.oss.driver.api.core.metadata.Node;
import com.datastax.oss.driver.api.core.session.Session;
import com.datastax.oss.driver.internal.core.metadata.DefaultEndPoint;
import com.datastax.oss.driver.internal.core.metadata.SniEndPoint;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.semconv.network.ServerAttributesExtractor;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// The Cassandra test container cannot exercise SNI. These tests use driver 4.3.1 to cover the SNI
// APIs that the 4.0 javaagent accesses by reflection.
@ExtendWith(MockitoExtension.class)
class CassandraEndpointAttributesTest {

  // SniEndPoint.resolve() always looks the proxy host up with
  // InetAddress.getAllByName(proxyAddress.getHostName()), so an unresolved literal keeps every test
  // offline and deterministic. A resolved address would make getHostName() do a reverse dns lookup
  // and the driver would then resolve whatever name came back.
  private static final InetSocketAddress PROXY_ADDRESS =
      InetSocketAddress.createUnresolved("127.0.0.1", 29042);

  @Mock private Metadata metadata;
  @Mock private Node coordinator;
  @Mock private EndPoint customEndPoint;
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
        serverAttributes(target(asList("node1.example.com:9042", "[::1]:9042")));

    if (emitStableDatabaseSemconv()) {
      assertThat(attributes.get(SERVER_ADDRESS)).isEqualTo("[::1]:9042,node1.example.com:9042");
      assertThat(attributes.get(SERVER_PORT)).isNull();
    } else {
      assertCoordinatorIsServer(attributes);
    }
  }

  @Test
  void configuredTargetIsAvailableWithoutExecutionInfo() {
    if (emitStableDatabaseSemconv()) {
      stubSessionNode();
    }
    CassandraRequest request =
        CassandraRequest.create(
            session, target(singletonList("cassandra.example.com:9042")), "SELECT 1");
    AttributesBuilder builder = Attributes.builder();

    ServerAttributesExtractor.create(new CassandraSqlAttributesGetter())
        .onStart(builder, Context.root(), request);

    Attributes attributes = builder.build();
    assertThat(attributes.get(SERVER_ADDRESS))
        .isEqualTo(emitStableDatabaseSemconv() ? "cassandra.example.com" : null);
    assertThat(attributes.get(SERVER_PORT)).isEqualTo(emitStableDatabaseSemconv() ? 9042L : null);
    if (emitStableDatabaseSemconv()) {
      verify(metadata).getNodes();
    }
  }

  @Test
  void sniEndPointIgnoresTheConfiguredTargetAndUsesBroadcastRpcAddress() {
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
    UUID hostId = UUID.fromString("2a1c1d5e-7b0e-4d3a-9a1f-2f5a6c8b0d31");
    when(coordinator.getEndPoint()).thenReturn(new SniEndPoint(PROXY_ADDRESS, hostId.toString()));
    if (emitStableDatabaseSemconv()) {
      when(coordinator.getBroadcastRpcAddress()).thenReturn(Optional.empty());
      when(coordinator.getHostId()).thenReturn(hostId);
    }

    Attributes attributes = serverAttributes(target(singletonList("proxy.example.com:29042")));

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

    Attributes attributes = serverAttributes(target(singletonList("proxy.example.com:29042")));

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

    Attributes attributes = serverAttributes(null);

    assertThat(attributes.get(SERVER_ADDRESS)).isEqualTo("node.example.com");
    assertThat(attributes.get(SERVER_PORT)).isEqualTo(9042L);
  }

  private Attributes serverAttributes(CassandraServerTarget serverTarget) {
    if (emitStableDatabaseSemconv() && serverTarget != null) {
      stubSessionNode();
    }
    CassandraRequest request = CassandraRequest.create(session, serverTarget, "SELECT 1");
    AttributesBuilder startAttributes = Attributes.builder();
    ServerAttributesExtractor.create(new CassandraSqlAttributesGetter())
        .onStart(startAttributes, Context.root(), request);
    AttributesBuilder endAttributes = Attributes.builder();
    CassandraAttributesExtractor.updateServerAddressAndPort(endAttributes, request, coordinator);
    return Attributes.builder()
        .putAll(startAttributes.build())
        .putAll(endAttributes.build())
        .build();
  }

  private void stubSessionNode() {
    when(session.getMetadata()).thenReturn(metadata);
    when(metadata.getNodes()).thenReturn(singletonMap(UUID.randomUUID(), coordinator));
  }

  private static CassandraServerTarget target(List<String> contactPoints) {
    return CassandraServerTarget.of(contactPoints);
  }

  private static void assertCoordinatorIsServer(Attributes attributes) {
    assertThat(attributes.get(SERVER_ADDRESS)).isEqualTo("127.0.0.1");
    assertThat(attributes.get(SERVER_PORT)).isEqualTo(9042L);
  }

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
