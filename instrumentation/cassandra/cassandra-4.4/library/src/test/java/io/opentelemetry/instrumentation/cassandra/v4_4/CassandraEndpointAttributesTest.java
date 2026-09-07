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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.datastax.oss.driver.api.core.cql.ExecutionInfo;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// The Cassandra test container cannot exercise SNI, so these tests cover endpoint mapping directly.
@ExtendWith(MockitoExtension.class)
class CassandraEndpointAttributesTest {

  private static final InetSocketAddress PROXY_ADDRESS =
      InetSocketAddress.createUnresolved("proxy.example.com", 29042);

  @Mock private ExecutionInfo executionInfo;
  @Mock private Node coordinator;
  @Mock private Session session;

  @Test
  void unconfiguredSessionUsesTheCoordinatorOnlyForLegacySemconv() throws UnknownHostException {
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
  void singleContactPointOmitsTheDefaultPort() throws UnknownHostException {
    if (!emitStableDatabaseSemconv()) {
      when(coordinator.getEndPoint()).thenReturn(new DefaultEndPoint(resolved(9042)));
    }

    Attributes attributes =
        serverAttributes(CassandraServerTarget.of(singletonList("cassandra.example.com:9042")));

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
        serverAttributes(CassandraServerTarget.of(asList("node1.example.com:9042", "[::1]:9042")));

    if (emitStableDatabaseSemconv()) {
      assertThat(attributes.get(SERVER_ADDRESS)).isEqualTo("node1.example.com,::1");
      assertThat(attributes.get(SERVER_PORT)).isNull();
    } else {
      assertCoordinatorIsServer(attributes);
    }
  }

  @Test
  void severalContactPointsInlineASharedNonDefaultPort() throws UnknownHostException {
    if (!emitStableDatabaseSemconv()) {
      when(coordinator.getEndPoint()).thenReturn(new DefaultEndPoint(resolved(9042)));
    }

    Attributes attributes =
        serverAttributes(
            CassandraServerTarget.of(asList("node1.example.com:9142", "node2.example.com:9142")));

    if (emitStableDatabaseSemconv()) {
      assertThat(attributes.get(SERVER_ADDRESS))
          .isEqualTo("node1.example.com:9142,node2.example.com:9142");
      assertThat(attributes.get(SERVER_PORT)).isNull();
    } else {
      assertCoordinatorIsServer(attributes);
    }
  }

  @Test
  void mixedPortsInlineIpv4AndIpv6Endpoints() throws UnknownHostException {
    if (!emitStableDatabaseSemconv()) {
      when(coordinator.getEndPoint()).thenReturn(new DefaultEndPoint(resolved(9042)));
    }

    Attributes attributes =
        serverAttributes(CassandraServerTarget.of(asList("[::1]:9042", "10.0.0.5:9142")));

    if (emitStableDatabaseSemconv()) {
      assertThat(attributes.get(SERVER_ADDRESS)).isEqualTo("[::1]:9042,10.0.0.5:9142");
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
                ? CassandraServerTarget.of(singletonList("cassandra.example.com:9042"))
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
  void sniEndPointIsNotUsedAsAStableServerTarget() {
    if (!emitStableDatabaseSemconv()) {
      when(coordinator.getEndPoint()).thenReturn(new SniEndPoint(PROXY_ADDRESS, "host-id"));
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
  void configuredTargetAndCoordinatorAreKeptSeparate() throws UnknownHostException {
    when(coordinator.getEndPoint()).thenReturn(new DefaultEndPoint(resolved(9042)));
    when(executionInfo.getCoordinator()).thenReturn(coordinator);

    Attributes attributes =
        serverAttributes(CassandraServerTarget.of(singletonList("configured.example.com:9142")));
    InetSocketAddress peer =
        new CassandraSqlAttributesGetter()
            .getNetworkPeerInetSocketAddress(
                CassandraRequest.create(session, null, "SELECT 1"), executionInfo);

    if (emitStableDatabaseSemconv()) {
      assertThat(attributes.get(SERVER_ADDRESS)).isEqualTo("configured.example.com");
      assertThat(attributes.get(SERVER_PORT)).isEqualTo(9142L);
    } else {
      assertCoordinatorIsServer(attributes);
    }
    assertThat(peer).isNotNull();
    assertThat(peer.getHostString()).isEqualTo("127.0.0.1");
    assertThat(peer.getPort()).isEqualTo(9042);
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
