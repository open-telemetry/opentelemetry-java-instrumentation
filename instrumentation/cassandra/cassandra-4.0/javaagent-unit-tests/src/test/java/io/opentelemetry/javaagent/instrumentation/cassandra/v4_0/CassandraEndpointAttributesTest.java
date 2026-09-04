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
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.datastax.oss.driver.api.core.cql.ExecutionInfo;
import com.datastax.oss.driver.api.core.metadata.EndPoint;
import com.datastax.oss.driver.api.core.metadata.Node;
import com.datastax.oss.driver.api.core.session.Session;
import com.datastax.oss.driver.internal.core.metadata.DefaultEndPoint;
import com.datastax.oss.driver.internal.core.metadata.SniEndPoint;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTarget;
import io.opentelemetry.instrumentation.api.semconv.network.ServerAttributesExtractor;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.List;
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

  @Mock private Node coordinator;
  @Mock private EndPoint customEndPoint;
  @Mock private ExecutionInfo executionInfo;
  @Mock private SniEndPoint sniEndPoint;
  @Mock private Session session;

  @Test
  void unconfiguredSessionOnlyUsesTheCoordinatorAddressInLegacyMode() throws UnknownHostException {
    if (!emitStableDatabaseSemconv()) {
      when(coordinator.getEndPoint()).thenReturn(new DefaultEndPoint(resolved(9042)));
    }

    Attributes attributes = serverAttributes(null);

    if (emitStableDatabaseSemconv()) {
      assertThat(attributes.get(SERVER_ADDRESS)).isNull();
      assertThat(attributes.get(SERVER_PORT)).isNull();
      verify(coordinator, never()).getEndPoint();
    } else {
      assertCoordinatorIsServer(attributes);
    }
  }

  @Test
  void singleDefaultPortContactPointOmitsItsPort() throws UnknownHostException {
    if (!emitStableDatabaseSemconv()) {
      when(coordinator.getEndPoint()).thenReturn(new DefaultEndPoint(resolved(9042)));
    }

    Attributes attributes = serverAttributes(target(singletonList("cassandra.example.com:9042")));

    if (emitStableDatabaseSemconv()) {
      assertThat(attributes.get(SERVER_ADDRESS)).isEqualTo("cassandra.example.com");
      assertThat(attributes.get(SERVER_PORT)).isNull();
    } else {
      assertCoordinatorIsServer(attributes);
    }
  }

  @Test
  void severalContactPointsAreOneTargetWithoutAPort() throws UnknownHostException {
    if (!emitStableDatabaseSemconv()) {
      when(coordinator.getEndPoint()).thenReturn(new DefaultEndPoint(resolved(9042)));
    }

    Attributes attributes =
        serverAttributes(target(asList("node1.example.com:9042", "[::1]:9042")));

    if (emitStableDatabaseSemconv()) {
      assertThat(attributes.get(SERVER_ADDRESS)).isEqualTo("::1,node1.example.com");
      assertThat(attributes.get(SERVER_PORT)).isNull();
    } else {
      assertCoordinatorIsServer(attributes);
    }
  }

  @Test
  void configuredTargetIsAvailableWithoutExecutionInfo() {
    CassandraRequest request =
        CassandraRequest.create(
            session,
            emitStableDatabaseSemconv()
                ? target(singletonList("cassandra.example.com:9042"))
                : null,
            "SELECT 1");
    AttributesBuilder builder = Attributes.builder();

    ServerAttributesExtractor.create(new CassandraSqlAttributesGetter())
        .onStart(builder, Context.root(), request);

    Attributes attributes = builder.build();
    assertThat(attributes.get(SERVER_ADDRESS))
        .isEqualTo(emitStableDatabaseSemconv() ? "cassandra.example.com" : null);
    assertThat(attributes.get(SERVER_PORT)).isNull();
  }

  @Test
  void sniEndPointPreservesTheConfiguredTarget() {
    if (!emitStableDatabaseSemconv()) {
      when(coordinator.getEndPoint()).thenReturn(new SniEndPoint(PROXY_ADDRESS, "host-id"));
    }

    Attributes attributes = serverAttributes(target(singletonList("proxy.example.com:29042")));

    if (emitStableDatabaseSemconv()) {
      assertThat(attributes.get(SERVER_ADDRESS)).isEqualTo("proxy.example.com");
      assertThat(attributes.get(SERVER_PORT)).isEqualTo(29042L);
      verify(coordinator, never()).getEndPoint();
    } else {
      assertProxyIsServer(attributes);
    }
  }

  @Test
  void unconfiguredSniEndPointDoesNotBecomeTheStableServer() {
    if (!emitStableDatabaseSemconv()) {
      when(coordinator.getEndPoint())
          .thenReturn(new SniEndPoint(PROXY_ADDRESS, "node1.example.com"));
    }

    Attributes attributes = serverAttributes(null);

    if (emitStableDatabaseSemconv()) {
      assertThat(attributes.get(SERVER_ADDRESS)).isNull();
      assertThat(attributes.get(SERVER_PORT)).isNull();
      verify(coordinator, never()).getEndPoint();
    } else {
      assertProxyIsServer(attributes);
    }
  }

  @Test
  void defaultEndPointRecordsTheCoordinatorAsNetworkPeerInBothModes() throws UnknownHostException {
    InetSocketAddress coordinatorAddress = resolved(9042);
    when(coordinator.getEndPoint()).thenReturn(new DefaultEndPoint(coordinatorAddress));
    when(executionInfo.getCoordinator()).thenReturn(coordinator);
    CassandraRequest request = CassandraRequest.create(session, null, "SELECT 1");

    InetSocketAddress peerAddress =
        new CassandraSqlAttributesGetter().getNetworkPeerInetSocketAddress(request, executionInfo);

    assertThat(peerAddress).isEqualTo(coordinatorAddress);
  }

  @Test
  void stableSniEndPointDoesNotResolveNetworkPeerAddress() {
    when(coordinator.getEndPoint()).thenReturn(sniEndPoint);
    when(executionInfo.getCoordinator()).thenReturn(coordinator);
    if (!emitStableDatabaseSemconv()) {
      when(sniEndPoint.resolve()).thenReturn(PROXY_ADDRESS);
    }
    CassandraRequest request = CassandraRequest.create(session, null, "SELECT 1");

    InetSocketAddress peerAddress =
        new CassandraSqlAttributesGetter().getNetworkPeerInetSocketAddress(request, executionInfo);

    if (emitStableDatabaseSemconv()) {
      assertThat(peerAddress).isNull();
      verify(sniEndPoint, never()).resolve();
    } else {
      assertThat(peerAddress).isEqualTo(PROXY_ADDRESS);
      verify(sniEndPoint).resolve();
    }
  }

  @Test
  void customEndPointOnlyUsesResolvedAddressForServerInLegacyMode() {
    if (!emitStableDatabaseSemconv()) {
      when(coordinator.getEndPoint()).thenReturn(customEndPoint);
      when(customEndPoint.resolve())
          .thenReturn(InetSocketAddress.createUnresolved("node.example.com", 9042));
    }

    Attributes attributes = serverAttributes(null);

    if (emitStableDatabaseSemconv()) {
      assertThat(attributes.get(SERVER_ADDRESS)).isNull();
      assertThat(attributes.get(SERVER_PORT)).isNull();
      verify(coordinator, never()).getEndPoint();
    } else {
      assertThat(attributes.get(SERVER_ADDRESS)).isEqualTo("node.example.com");
      assertThat(attributes.get(SERVER_PORT)).isEqualTo(9042L);
      verify(customEndPoint).resolve();
    }
  }

  private Attributes serverAttributes(DbServerTarget serverTarget) {
    CassandraRequest request =
        CassandraRequest.create(
            session, emitStableDatabaseSemconv() ? serverTarget : null, "SELECT 1");
    AttributesBuilder startAttributes = Attributes.builder();
    ServerAttributesExtractor.create(new CassandraSqlAttributesGetter())
        .onStart(startAttributes, Context.root(), request);
    AttributesBuilder endAttributes = Attributes.builder();
    CassandraAttributesExtractor.updateServerAddressAndPort(endAttributes, coordinator);
    return Attributes.builder()
        .putAll(startAttributes.build())
        .putAll(endAttributes.build())
        .build();
  }

  private static DbServerTarget target(List<String> contactPoints) {
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
