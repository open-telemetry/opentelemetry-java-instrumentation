/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jdbc.test;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.DbAttributes.DB_NAMESPACE;
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_SUMMARY;
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_TEXT;
import static io.opentelemetry.semconv.DbAttributes.DB_RESPONSE_STATUS_CODE;
import static io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLWarning;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
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

  @Test
  void streamingResultSetRemainsConsumable() throws Exception {
    String sql = "SELECT 1 UNION ALL SELECT 2";
    try (Connection connection = connect(source, "");
        Statement statement = connection.createStatement()) {
      statement.setFetchSize(Integer.MIN_VALUE);
      try (ResultSet result = statement.executeQuery(sql)) {
        assertThat(result.next()).isTrue();
        assertThat(result.getInt(1)).isEqualTo(1);
        assertThat(result.next()).isTrue();
        assertThat(result.getInt(1)).isEqualTo(2);
        assertThat(result.next()).isFalse();
      }
    }

    assertSuccessfulSpan();
  }

  @Test
  void multipleResultsAndUpdateCountsRemainConsumable() throws Exception {
    String sql =
        "SELECT 1; UPDATE jdbc_warning_test SET value = value + 1; "
            + "SELECT value FROM jdbc_warning_test";
    try (Connection connection = connect(source, "&allowMultiQueries=true");
        Statement statement = connection.createStatement()) {
      createTable(statement);

      assertThat(statement.execute(sql)).isTrue();

      try (ResultSet result = statement.getResultSet()) {
        assertThat(result.next()).isTrue();
        assertThat(result.getInt(1)).isEqualTo(1);
        assertThat(result.next()).isFalse();
      }
      assertThat(statement.getMoreResults()).isFalse();
      assertThat(statement.getUpdateCount()).isEqualTo(1);
      assertThat(statement.getMoreResults()).isTrue();
      try (ResultSet result = statement.getResultSet()) {
        assertThat(result.next()).isTrue();
        assertThat(result.getInt(1)).isEqualTo(2);
        assertThat(result.next()).isFalse();
      }
      assertThat(statement.getMoreResults()).isFalse();
      assertThat(statement.getUpdateCount()).isEqualTo(-1);
    }

    assertSuccessfulSpan();
  }

  @Test
  void warningIsCapturedWithoutChangingApplicationWarningChain() throws Exception {
    try (Connection connection = connect(source, "");
        Statement statement = connection.createStatement()) {
      createTable(statement);

      String sql = "INSERT IGNORE INTO jdbc_warning_test VALUES (1), (1)";
      assertThat(statement.executeUpdate(sql)).isZero();

      SQLWarning warning = statement.getWarnings();
      assertWarning(warning);
      assertWarning(warning.getNextWarning());
      assertWarningSpan();
    }
  }

  private static Connection connect(GenericContainer<?> mysql, String options) throws Exception {
    String url =
        "jdbc:mysql://"
            + mysql.getHost()
            + ":"
            + mysql.getMappedPort(3306)
            + "/shop?sslMode=DISABLED&allowPublicKeyRetrieval=true&openTelemetry=DISABLED"
            + options;
    return DriverManager.getConnection(url, "root", PASSWORD);
  }

  private static void createTable(Statement statement) throws Exception {
    statement.execute("CREATE TEMPORARY TABLE jdbc_warning_test (value INT PRIMARY KEY)");
    testing.waitAndAssertTraces(
        trace -> trace.hasSpansSatisfyingExactly(span -> span.hasName("CREATE TEMPORARY TABLE")));
    testing.clearData();

    statement.executeUpdate("INSERT INTO jdbc_warning_test VALUES (1)");
    testing.waitAndAssertTraces(
        trace -> trace.hasSpansSatisfyingExactly(span -> span.hasName("INSERT jdbc_warning_test")));
    testing.clearData();
  }

  private static void assertWarning(SQLWarning warning) {
    assertThat((Throwable) warning).isNotNull();
    assertThat(warning.getErrorCode()).isEqualTo(1062);
  }

  private static void assertWarningSpan() {
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasKind(SpanKind.CLIENT)
                        .hasNoParent()
                        .hasAttributesSatisfying(
                            equalTo(DB_RESPONSE_STATUS_CODE, "1062"), equalTo(ERROR_TYPE, null))
                        .hasStatus(StatusData.unset())));
  }

  private static void assertSuccessfulSpan() {
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfying(
                            equalTo(DB_RESPONSE_STATUS_CODE, null), equalTo(ERROR_TYPE, null))
                        .hasStatus(StatusData.unset())));
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
