/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v4_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static java.util.stream.Collectors.joining;
import static org.assertj.core.api.Assertions.assertThat;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.config.DefaultDriverOption;
import com.datastax.oss.driver.api.core.config.DriverConfigLoader;
import com.datastax.oss.driver.internal.core.config.typesafe.DefaultDriverConfigLoader;
import com.datastax.oss.driver.api.core.metadata.EndPoint;
import io.opentelemetry.cassandra.common.v4_0.AbstractCassandraTest;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class CassandraTest extends AbstractCassandraTest {

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
  void responsePeerComesFromTheChannel() {
    AtomicReference<SocketAddress> fallbackAddress =
        new AtomicReference<>(new InetSocketAddress(cassandraHost, cassandraPort));
    EndPoint customEndPoint =
        new EndPoint() {
          @Override
          public SocketAddress resolve() {
            return fallbackAddress.get();
          }

          @Override
          public String asMetricPrefix() {
            return "response_peer_test";
          }
        };
    CqlSession session =
        CqlSession.builder()
            .addContactEndPoint(customEndPoint)
            .withLocalDatacenter("datacenter1")
            .build();
    cleanup.deferCleanup(session);
    fallbackAddress.set(InetSocketAddress.createUnresolved("fallback.invalid", cassandraPort));

    session.execute("SELECT release_version FROM system.local");
    testing.waitForTraces(1);

    assertThat(testing.spans())
        .singleElement()
        .satisfies(
            span -> {
              assertThat(span.getAttributes().get(NETWORK_PEER_ADDRESS)).isEqualTo(cassandraIp);
              assertThat(span.getAttributes().get(NETWORK_PEER_PORT)).isEqualTo(cassandraPort);
            });
  }
}
