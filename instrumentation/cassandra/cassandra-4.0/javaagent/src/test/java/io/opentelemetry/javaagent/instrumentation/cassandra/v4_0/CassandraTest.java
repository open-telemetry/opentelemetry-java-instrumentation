/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v4_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldDatabaseSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_TYPE;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static java.util.Collections.emptySet;
import static java.util.stream.Collectors.joining;
import static org.assertj.core.api.Assertions.assertThat;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.config.DefaultDriverOption;
import com.datastax.oss.driver.api.core.config.DriverConfigLoader;
import com.datastax.oss.driver.internal.core.config.typesafe.DefaultDriverConfigLoader;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.metadata.EndPoint;
import com.datastax.oss.driver.api.core.metadata.Node;
import com.datastax.oss.driver.internal.core.metadata.DefaultEndPoint;
import io.opentelemetry.cassandra.common.v4_0.AbstractCassandraTest;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class CassandraTest extends AbstractCassandraTest {

  private static final String PEER_QUERY = "SELECT release_version FROM system.local";

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.cassandra-4.0";
  }

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  @Test
  void configuredContactPointsRemainSeparateFromTheCoordinator() {
    DriverConfigLoader configLoader =
        DefaultDriverConfigLoader.builder()
            .withDuration(DefaultDriverOption.REQUEST_TIMEOUT, Duration.ofSeconds(0))
            .withDuration(DefaultDriverOption.CONNECTION_INIT_QUERY_TIMEOUT, Duration.ofSeconds(10))
            .build();
    CqlSession session =
        CqlSession.builder()
            .addContactPoint(new InetSocketAddress(cassandraHost, cassandraPort))
            .addContactPoint(new InetSocketAddress("127.0.0.2", 9042))
            .withConfigLoader(configLoader)
            .withLocalDatacenter("datacenter1")
            .build();
    cleanup.deferCleanup(session);

    session.execute("DROP KEYSPACE IF EXISTS configured_target_test");

    String configuredTarget =
        Stream.of("127.0.0.2:9042", cassandraHost + ':' + cassandraPort)
            .sorted(String::compareTo)
            .collect(joining(","));
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(emitStableDatabaseSemconv() ? "DROP KEYSPACE" : "DROP")
                        .satisfies(
                            spanData -> {
                              assertThat(spanData.getAttributes().get(SERVER_ADDRESS))
                                  .isEqualTo(
                                      emitStableDatabaseSemconv()
                                          ? configuredTarget
                                          : cassandraHost);
                              assertThat(spanData.getAttributes().get(SERVER_PORT))
                                  .isEqualTo(
                                      emitStableDatabaseSemconv() ? null : (long) cassandraPort);
                              assertThat(spanData.getAttributes().get(NETWORK_PEER_ADDRESS))
                                  .isEqualTo(cassandraIp);
                              assertThat(spanData.getAttributes().get(NETWORK_PEER_PORT))
                                  .isEqualTo((long) cassandraPort);
                            })));
  }

  @Test
  void responsePeerComesFromTheChannel() throws ReflectiveOperationException {
    CqlSession instrumentedSession = getSession(null);
    CqlSession delegate = getDelegate(instrumentedSession);
    InetSocketAddress coordinatorAddress = new InetSocketAddress("127.0.0.2", 19042);
    CqlSession mutatingDelegate =
        (CqlSession)
            Proxy.newProxyInstance(
                delegate.getClass().getClassLoader(),
                new Class<?>[] {CqlSession.class},
                (proxy, method, args) -> {
                  Object result = method.invoke(delegate, args);
                  if ("execute".equals(method.getName())
                      && method.getParameterCount() == 1
                      && PEER_QUERY.equals(args[0])) {
                    ResultSet resultSet = (ResultSet) result;
                    setEndPoint(
                        resultSet.getExecutionInfo().getCoordinator(),
                        new DefaultEndPoint(coordinatorAddress));
                  }
                  return result;
                });
    CqlSession session = TracingCqlSession.wrapSession(mutatingDelegate, emptySet());
    cleanup.deferCleanup(session);
    Node node = delegate.getMetadata().getNodes().values().iterator().next();
    EndPoint originalEndPoint = node.getEndPoint();
    ResultSet result;
    try {
      result = session.execute(PEER_QUERY);
    } finally {
      setEndPoint(node, originalEndPoint);
    }
    testing.waitForTraces(1);

    InetSocketAddress responsePeer =
        CassandraResponsePeers.getExecutionInfoPeer(result.getExecutionInfo());
    assertThat(responsePeer).isNotNull();
    assertThat(responsePeer.getAddress().getHostAddress()).isEqualTo(cassandraIp);
    assertThat(responsePeer.getPort()).isEqualTo(cassandraPort);

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
                                  point.hasAttributesSatisfying(
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
