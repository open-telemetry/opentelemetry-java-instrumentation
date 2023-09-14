/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.testing.cassandra.v4_4;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.SemanticAttributes.DB_CASSANDRA_CONSISTENCY_LEVEL;
import static io.opentelemetry.semconv.SemanticAttributes.DB_CASSANDRA_COORDINATOR_DC;
import static io.opentelemetry.semconv.SemanticAttributes.DB_CASSANDRA_COORDINATOR_ID;
import static io.opentelemetry.semconv.SemanticAttributes.DB_CASSANDRA_IDEMPOTENCE;
import static io.opentelemetry.semconv.SemanticAttributes.DB_CASSANDRA_PAGE_SIZE;
import static io.opentelemetry.semconv.SemanticAttributes.DB_CASSANDRA_SPECULATIVE_EXECUTION_COUNT;
import static io.opentelemetry.semconv.SemanticAttributes.DB_CASSANDRA_TABLE;
import static io.opentelemetry.semconv.SemanticAttributes.DB_NAME;
import static io.opentelemetry.semconv.SemanticAttributes.DB_OPERATION;
import static io.opentelemetry.semconv.SemanticAttributes.DB_STATEMENT;
import static io.opentelemetry.semconv.SemanticAttributes.DB_SYSTEM;
import static io.opentelemetry.semconv.SemanticAttributes.NET_SOCK_PEER_ADDR;
import static io.opentelemetry.semconv.SemanticAttributes.NET_SOCK_PEER_NAME;
import static io.opentelemetry.semconv.SemanticAttributes.NET_SOCK_PEER_PORT;
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

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("provideReactiveParameters")
  @SuppressWarnings("deprecation") // until old http semconv are dropped in 2.0
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
                                equalTo(NET_SOCK_PEER_ADDR, "127.0.0.1"),
                                equalTo(NET_SOCK_PEER_NAME, "localhost"),
                                equalTo(NET_SOCK_PEER_PORT, cassandraPort),
                                equalTo(DB_SYSTEM, "cassandra"),
                                equalTo(DB_NAME, parameter.keyspace),
                                equalTo(DB_STATEMENT, parameter.expectedStatement),
                                equalTo(DB_OPERATION, parameter.operation),
                                equalTo(DB_CASSANDRA_CONSISTENCY_LEVEL, "LOCAL_ONE"),
                                equalTo(DB_CASSANDRA_COORDINATOR_DC, "datacenter1"),
                                satisfies(
                                    DB_CASSANDRA_COORDINATOR_ID,
                                    val -> val.isInstanceOf(String.class)),
                                satisfies(
                                    DB_CASSANDRA_IDEMPOTENCE,
                                    val -> val.isInstanceOf(Boolean.class)),
                                equalTo(DB_CASSANDRA_PAGE_SIZE, 5000),
                                equalTo(DB_CASSANDRA_SPECULATIVE_EXECUTION_COUNT, 0),
                                equalTo(DB_CASSANDRA_TABLE, parameter.table)),
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
                    "DB Query",
                    null,
                    null))),
        Arguments.of(
            named(
                "Create keyspace with replication",
                new Parameter(
                    null,
                    "CREATE KEYSPACE reactive_test WITH REPLICATION = {'class':'SimpleStrategy', 'replication_factor':3}",
                    "CREATE KEYSPACE reactive_test WITH REPLICATION = {?:?, ?:?}",
                    "DB Query",
                    null,
                    null))),
        Arguments.of(
            named(
                "Create table",
                new Parameter(
                    "reactive_test",
                    "CREATE TABLE reactive_test.users ( id UUID PRIMARY KEY, name text )",
                    "CREATE TABLE reactive_test.users ( id UUID PRIMARY KEY, name text )",
                    "reactive_test",
                    null,
                    null))),
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
}
