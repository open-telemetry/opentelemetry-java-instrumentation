/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v4_0;

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
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.joining;
import static org.assertj.core.api.Assertions.assertThat;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.config.DefaultDriverOption;
import com.datastax.oss.driver.api.core.config.DriverConfigLoader;
import com.datastax.oss.driver.internal.core.config.typesafe.DefaultDriverConfigLoader;
import io.opentelemetry.cassandra.common.v4_0.AbstractCassandraTest;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
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
            .withStringList(
                DefaultDriverOption.CONTACT_POINTS,
                singletonList(cassandraIp + ':' + cassandraPort))
            .withDuration(DefaultDriverOption.REQUEST_TIMEOUT, Duration.ofSeconds(0))
            .withDuration(DefaultDriverOption.CONNECTION_INIT_QUERY_TIMEOUT, Duration.ofSeconds(10))
            .build();
    CqlSession session =
        CqlSession.builder()
            .addContactPoint(new InetSocketAddress(cassandraHost, cassandraPort))
            .withConfigLoader(configLoader)
            .withLocalDatacenter("datacenter1")
            .build();
    cleanup.deferCleanup(session);

    session.execute("DROP KEYSPACE IF EXISTS configured_target_test");

    String configuredTarget =
        Stream.of(cassandraIp + ':' + cassandraPort, cassandraHost + ':' + cassandraPort)
            .sorted()
            .collect(joining(","));
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("DROP KEYSPACE")
                        .satisfies(
                            spanData -> {
                              assertThat(spanData.getAttributes().get(SERVER_ADDRESS))
                                  .isEqualTo(configuredTarget);
                              assertThat(spanData.getAttributes().get(SERVER_PORT)).isEqualTo(null);
                              assertThat(spanData.getAttributes().get(NETWORK_PEER_ADDRESS))
                                  .isEqualTo(cassandraIp);
                              assertThat(spanData.getAttributes().get(NETWORK_PEER_PORT))
                                  .isEqualTo((long) cassandraPort);
                            })));
  }

  @Test
  void responsePeerComesFromTheChannel() {
    CqlSession session = getSession(null);
    cleanup.deferCleanup(session);
    session.execute(PEER_QUERY);
    testing.waitForTraces(1);

    assertThat(testing.spans())
        .singleElement()
        .satisfies(
            span -> {
              assertThat(span.getAttributes().get(NETWORK_PEER_ADDRESS)).isEqualTo(cassandraIp);
              assertThat(span.getAttributes().get(NETWORK_PEER_PORT)).isEqualTo(cassandraPort);
              assertThat(span.getAttributes().get(NETWORK_TYPE)).isEqualTo(null);
            });
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
                                    equalTo(NETWORK_PEER_PORT, cassandraPort),
                                    equalTo(SERVER_ADDRESS, cassandraHost),
                                    equalTo(SERVER_PORT, cassandraPort)))));
  }
}
