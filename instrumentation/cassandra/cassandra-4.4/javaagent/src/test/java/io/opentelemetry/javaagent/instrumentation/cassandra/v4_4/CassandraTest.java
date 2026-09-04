/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v4_4;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldDatabaseSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.DbAttributes.DB_COLLECTION_NAME;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_NAME;
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_SUMMARY;
import static io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_TYPE;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static org.assertj.core.api.Assertions.assertThat;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.metadata.EndPoint;
import com.datastax.oss.driver.api.core.metadata.Node;
import com.datastax.oss.driver.internal.core.metadata.DefaultEndPoint;
import com.datastax.oss.driver.internal.core.metadata.SniEndPoint;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.testing.cassandra.v4_4.AbstractCassandra44Test;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class CassandraTest extends AbstractCassandra44Test {

  private static final String PEER_QUERY = "SELECT release_version FROM system.local";

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.cassandra-4.4";
  }

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  @Test
  void sniNetworkPeerIsTheResponseChannelProxy() {
    InetSocketAddress proxyAddress = new InetSocketAddress(cassandraHost, cassandraPort);
    CqlSession session =
        CqlSession.builder()
            .addContactEndPoint(new SniEndPoint(proxyAddress, "localhost"))
            .withLocalDatacenter("datacenter1")
            .build();
    cleanup.deferCleanup(session);

    session.execute(PEER_QUERY);
    testing.waitForTraces(1);

    assertThat(testing.spans())
        .singleElement()
        .satisfies(
            span -> {
              String peerAddress = span.getAttributes().get(NETWORK_PEER_ADDRESS);
              if (emitStableDatabaseSemconv()) {
                assertThat(peerAddress).isIn("127.0.0.1", "0:0:0:0:0:0:0:1", "::1");
              } else {
                assertThat(peerAddress).isNull();
              }
              assertThat(span.getAttributes().get(NETWORK_PEER_PORT))
                  .isEqualTo(emitStableDatabaseSemconv() ? (long) cassandraPort : null);
              assertThat(span.getAttributes().get(NETWORK_TYPE))
                  .isEqualTo(
                      emitOldDatabaseSemconv() && emitStableDatabaseSemconv()
                          ? (peerAddress.contains(":") ? "ipv6" : "ipv4")
                          : null);
              assertThat(span.getAttributes().get(SERVER_ADDRESS))
                  .isEqualTo(emitStableDatabaseSemconv() ? null : proxyAddress.getHostString());
              assertThat(span.getAttributes().get(SERVER_PORT))
                  .isEqualTo(emitStableDatabaseSemconv() ? null : (long) cassandraPort);
            });
  }

  @Test
  void responsePeerComesFromTheChannel() throws ReflectiveOperationException {
    CqlSession session = getSession(null);
    cleanup.deferCleanup(session);
    CqlSession delegate = getDelegate(session);
    InetSocketAddress coordinatorAddress = new InetSocketAddress("127.0.0.2", 19042);
    Node node = delegate.getMetadata().getNodes().values().iterator().next();
    EndPoint originalEndPoint = node.getEndPoint();
    try {
      setEndPoint(node, new DefaultEndPoint(coordinatorAddress));
      session.execute(PEER_QUERY);
    } finally {
      setEndPoint(node, originalEndPoint);
    }
    testing.waitForTraces(1);

    assertThat(testing.spans())
        .singleElement()
        .satisfies(
            span -> {
              assertThat(span.getAttributes().get(NETWORK_PEER_ADDRESS))
                  .isEqualTo(
                      emitStableDatabaseSemconv()
                          ? cassandraIp
                          : coordinatorAddress.getAddress().getHostAddress());
              assertThat(span.getAttributes().get(NETWORK_PEER_PORT))
                  .isEqualTo(
                      emitStableDatabaseSemconv() ? cassandraPort : coordinatorAddress.getPort());
              assertThat(span.getAttributes().get(NETWORK_TYPE))
                  .isEqualTo(emitOldDatabaseSemconv() ? "ipv4" : null);
            });
    if (emitStableDatabaseSemconv()) {
      testing.waitAndAssertMetrics(
          getInstrumentationName(),
          metric ->
              metric
                  .hasName("db.client.operation.duration")
                  .hasHistogramSatisfying(
                      histogram ->
                          histogram.hasPointsSatisfying(
                              point ->
                                  point.hasAttributesSatisfyingExactly(
                                      equalTo(DB_COLLECTION_NAME, "system.local"),
                                      equalTo(DB_OPERATION_NAME, "SELECT"),
                                      equalTo(DB_QUERY_SUMMARY, "SELECT system.local"),
                                      equalTo(DB_SYSTEM_NAME, "cassandra"),
                                      equalTo(NETWORK_PEER_ADDRESS, cassandraIp),
                                      equalTo(NETWORK_PEER_PORT, cassandraPort)))));
    }
  }

  private static CqlSession getDelegate(CqlSession session) throws IllegalAccessException {
    InvocationHandler invocationHandler = Proxy.getInvocationHandler(session);
    for (Field field : invocationHandler.getClass().getDeclaredFields()) {
      if (CqlSession.class.isAssignableFrom(field.getType())) {
        field.setAccessible(true);
        return (CqlSession) field.get(invocationHandler);
      }
    }
    throw new IllegalStateException("Could not find the delegate session");
  }

  private static void setEndPoint(Node node, EndPoint endPoint)
      throws ReflectiveOperationException {
    Field field = node.getClass().getDeclaredField("endPoint");
    field.setAccessible(true);
    field.set(node, endPoint);
  }
}
