/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.testing.cassandra.v4_4;

import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStable;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_TYPE;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_CASSANDRA_CONSISTENCY_LEVEL;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_CASSANDRA_COORDINATOR_DC;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_CASSANDRA_COORDINATOR_ID;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_CASSANDRA_IDEMPOTENCE;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_CASSANDRA_PAGE_SIZE;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_CASSANDRA_SPECULATIVE_EXECUTION_COUNT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_CASSANDRA_TABLE;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_NAME;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_STATEMENT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Named.named;

import com.datastax.oss.driver.api.core.CqlSession;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.cassandra.v4.common.AbstractCassandraTest;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import reactor.core.publisher.Flux;

public abstract class AbstractCassandra44Test extends AbstractCassandraTest {

  @SuppressWarnings("deprecation") // using deprecated semconv
  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("provideReactiveParameters")
  void reactiveTest(Parameter parameter) {
    CqlSession session = getSession(parameter.keyspace);

    testing()
        .runWithSpan(
            "parent",
            () ->
                Flux.from(session.executeReactive(parameter.statement))
                    .doOnComplete(() -> testing().runWithSpan("child", () -> {}))
                    .blockLast());

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                    span ->
                        span.hasName(parameter.spanName)
                            .hasKind(SpanKind.CLIENT)
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfyingExactly(
                                satisfies(
                                    NETWORK_TYPE,
                                    val ->
                                        val.satisfiesAnyOf(
                                            v -> assertThat(v).isEqualTo("ipv4"),
                                            v -> assertThat(v).isEqualTo("ipv6"))),
                                equalTo(SERVER_ADDRESS, cassandraHost),
                                equalTo(SERVER_PORT, cassandraPort),
                                equalTo(NETWORK_PEER_ADDRESS, cassandraIp),
                                equalTo(NETWORK_PEER_PORT, cassandraPort),
                                equalTo(maybeStable(DB_SYSTEM), "cassandra"),
                                equalTo(maybeStable(DB_NAME), parameter.keyspace),
                                equalTo(maybeStable(DB_STATEMENT), parameter.expectedStatement),
                                equalTo(maybeStable(DB_OPERATION), parameter.operation),
                                equalTo(maybeStable(DB_CASSANDRA_CONSISTENCY_LEVEL), "LOCAL_ONE"),
                                equalTo(maybeStable(DB_CASSANDRA_COORDINATOR_DC), "datacenter1"),
                                satisfies(
                                    maybeStable(DB_CASSANDRA_COORDINATOR_ID),
                                    val -> val.isInstanceOf(String.class)),
                                satisfies(
                                    maybeStable(DB_CASSANDRA_IDEMPOTENCE),
                                    val -> val.isInstanceOf(Boolean.class)),
                                equalTo(maybeStable(DB_CASSANDRA_PAGE_SIZE), 5000),
                                equalTo(maybeStable(DB_CASSANDRA_SPECULATIVE_EXECUTION_COUNT), 0),
                                equalTo(maybeStable(DB_CASSANDRA_TABLE), parameter.table)),
                    span ->
                        span.hasName("child")
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(0))));

    session.close();
  }

  private static Stream<Arguments> provideReactiveParameters() {
    return Stream.of(
        Arguments.of(
            named(
                "Drop keyspace if exists",
                new Parameter(
                    null,
                    "DROP KEYSPACE IF EXISTS reactive_test",
                    "DROP KEYSPACE IF EXISTS reactive_test",
                    "DROP",
                    "DROP",
                    null))),
        Arguments.of(
            named(
                "Create keyspace with replication",
                new Parameter(
                    null,
                    "CREATE KEYSPACE reactive_test WITH REPLICATION = {'class':'SimpleStrategy', 'replication_factor':3}",
                    "CREATE KEYSPACE reactive_test WITH REPLICATION = {?:?, ?:?}",
                    "CREATE",
                    "CREATE",
                    null))),
        Arguments.of(
            named(
                "Create table",
                new Parameter(
                    "reactive_test",
                    "CREATE TABLE reactive_test.users ( id UUID PRIMARY KEY, name text )",
                    "CREATE TABLE reactive_test.users ( id UUID PRIMARY KEY, name text )",
                    "CREATE TABLE reactive_test.users",
                    "CREATE TABLE",
                    "reactive_test.users"))),
        Arguments.of(
            named(
                "Insert data",
                new Parameter(
                    "reactive_test",
                    "INSERT INTO reactive_test.users (id, name) values (uuid(), 'alice')",
                    "INSERT INTO reactive_test.users (id, name) values (uuid(), ?)",
                    "INSERT reactive_test.users",
                    "INSERT",
                    "reactive_test.users"))),
        Arguments.of(
            named(
                "Select data",
                new Parameter(
                    "reactive_test",
                    "SELECT * FROM users where name = 'alice' ALLOW FILTERING",
                    "SELECT * FROM users where name = ? ALLOW FILTERING",
                    "SELECT reactive_test.users",
                    "SELECT",
                    "users"))));
  }

  // TODO (trask) this is causing sporadic test failures
  // @Override
  // protected CqlSessionBuilder addContactPoint(CqlSessionBuilder sessionBuilder) {
  //   InetSocketAddress address = new InetSocketAddress("localhost", cassandraPort);
  //   sessionBuilder.addContactEndPoint(new SniEndPoint(address, "localhost"));
  //   return sessionBuilder;
  // }
}
