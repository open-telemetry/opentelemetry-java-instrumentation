/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jdbc.test;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.DbAttributes.DB_NAMESPACE;
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_SUMMARY;
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_TEXT;
import static io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

class MySqlReplicationTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static final String PASSWORD = UUID.randomUUID().toString();
  private static final GenericContainer<?> source = mysql();
  private static final GenericContainer<?> replica = mysql();

  private static GenericContainer<?> mysql() {
    return new GenericContainer<>("mysql:8.0.32")
        .withEnv("MYSQL_ROOT_PASSWORD", PASSWORD)
        .withEnv("MYSQL_DATABASE", "shop")
        .withExposedPorts(3306)
        .waitingFor(Wait.forLogMessage(".*ready for connections.*port: 3306.*", 1));
  }

  @BeforeAll
  static void start() {
    source.start();
    replica.start();
  }

  @AfterAll
  static void stop() {
    replica.stop();
    source.stop();
  }

  @ParameterizedTest
  @CsvSource({"false,false", "true,false", "true,true"})
  void configuredTargetSurvivesRouting(boolean prepared, boolean serverPrepared) throws Exception {
    String target =
        source.getHost()
            + ":"
            + source.getMappedPort(3306)
            + ","
            + replica.getHost()
            + ":"
            + replica.getMappedPort(3306);
    String url =
        "jdbc:mysql:replication://"
            + target
            + "/shop?sslMode=DISABLED&allowPublicKeyRetrieval=true&openTelemetry=DISABLED&useServerPrepStmts="
            + serverPrepared;
    try (Connection connection = DriverManager.getConnection(url, "root", PASSWORD)) {
      String sourceId = query(connection, false, prepared, target);
      String replicaId = query(connection, true, prepared, target);
      assertThat(replicaId).isNotEqualTo(sourceId);
      assertThat(query(connection, false, prepared, target)).isEqualTo(sourceId);
    }
  }

  private static String query(
      Connection connection, boolean readOnly, boolean prepared, String target) throws Exception {
    connection.setReadOnly(readOnly);
    String sql = "SELECT @@server_uuid";
    String serverId;
    try (Statement statement =
        prepared ? connection.prepareStatement(sql) : connection.createStatement()) {
      assertThat(statement.getConnection().unwrap(Connection.class))
          .isSameAs(connection.unwrap(Connection.class));
      try (ResultSet result =
          prepared ? ((PreparedStatement) statement).executeQuery() : statement.executeQuery(sql)) {
        assertThat(result.next()).isTrue();
        serverId = result.getString(1);
      }
    }

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("SELECT")
                        .hasKind(SpanKind.CLIENT)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM_NAME, "mysql"),
                            equalTo(DB_NAMESPACE, "shop"),
                            equalTo(DB_QUERY_TEXT, sql),
                            equalTo(DB_QUERY_SUMMARY, "SELECT"),
                            equalTo(SERVER_ADDRESS, target))));
    testing.clearData();
    return serverId;
  }
}
