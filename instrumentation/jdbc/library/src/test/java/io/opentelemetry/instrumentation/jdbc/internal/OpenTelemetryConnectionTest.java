/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal;

import static io.opentelemetry.instrumentation.jdbc.internal.JdbcInstrumenterFactory.createStatementInstrumenter;
import static io.opentelemetry.instrumentation.jdbc.internal.JdbcInstrumenterFactory.createTransactionInstrumenter;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.jdbc.TestConnection;
import io.opentelemetry.instrumentation.jdbc.internal.dbinfo.DbInfo;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class OpenTelemetryConnectionTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  private static final List<String> executedSql = new ArrayList<>();

  @BeforeEach
  void resetTest() {
    executedSql.clear();
  }

  @SuppressWarnings("unchecked")
  @Test
  void testVerifyCreateStatementReturnsOtelWrapper() throws Exception {
    OpenTelemetry openTelemetry = OpenTelemetry.propagating(ContextPropagators.noop());
    OpenTelemetryConnection connection = getConnection(openTelemetry);

    assertThat(connection.createStatement()).isInstanceOf(OpenTelemetryStatement.class);
    assertThat(connection.createStatement(0, 0)).isInstanceOf(OpenTelemetryStatement.class);
    assertThat(connection.createStatement(0, 0, 0)).isInstanceOf(OpenTelemetryStatement.class);
    assertThat(((OpenTelemetryStatement<Statement>) connection.createStatement()).instrumenter)
        .isEqualTo(connection.statementInstrumenter);

    connection.close();
  }

  @SuppressWarnings("unchecked")
  @Test
  void testVerifyPrepareStatementReturnsOtelWrapper() throws Exception {
    OpenTelemetry openTelemetry = OpenTelemetry.propagating(ContextPropagators.noop());
    OpenTelemetryConnection connection = getConnection(openTelemetry);

    String query = "SELECT * FROM users";

    assertThat(connection.prepareStatement(query))
        .isInstanceOf(OpenTelemetryPreparedStatement.class);
    assertThat(connection.prepareStatement(query, new int[] {0}))
        .isInstanceOf(OpenTelemetryPreparedStatement.class);
    assertThat(connection.prepareStatement(query, new String[] {"id"}))
        .isInstanceOf(OpenTelemetryPreparedStatement.class);
    assertThat(connection.prepareStatement(query, 0))
        .isInstanceOf(OpenTelemetryPreparedStatement.class);
    assertThat(connection.prepareStatement(query, 0, 0))
        .isInstanceOf(OpenTelemetryPreparedStatement.class);
    assertThat(connection.prepareStatement(query, 0, 0, 0))
        .isInstanceOf(OpenTelemetryPreparedStatement.class);
    assertThat(
            ((OpenTelemetryStatement<Statement>) connection.prepareStatement(query)).instrumenter)
        .isEqualTo(connection.statementInstrumenter);

    connection.close();
  }

  @SuppressWarnings("unchecked")
  @Test
  void testVerifyPrepareCallReturnsOtelWrapper() throws Exception {
    OpenTelemetry openTelemetry = OpenTelemetry.propagating(ContextPropagators.noop());
    OpenTelemetryConnection connection = getConnection(openTelemetry);

    String query = "SELECT * FROM users";

    assertThat(connection.prepareCall(query)).isInstanceOf(OpenTelemetryCallableStatement.class);

    assertThat(connection.prepareCall(query)).isInstanceOf(OpenTelemetryCallableStatement.class);
    assertThat(connection.prepareCall(query, 0, 0))
        .isInstanceOf(OpenTelemetryCallableStatement.class);
    assertThat(connection.prepareCall(query, 0, 0, 0))
        .isInstanceOf(OpenTelemetryCallableStatement.class);
    assertThat(((OpenTelemetryStatement<Statement>) connection.prepareCall(query)).instrumenter)
        .isEqualTo(connection.statementInstrumenter);

    connection.close();
  }

  private static DbInfo getDbInfo() {
    return DbInfo.builder()
        .system("my_system")
        .subtype("my_sub_type")
        .shortUrl("my_connection_string")
        .user("my_user")
        .name("my_name")
        .db("my_db")
        .host("my_host")
        .port(1234)
        .build();
  }

  private static OpenTelemetryConnection getConnection(OpenTelemetry openTelemetry) {
    Instrumenter<DbRequest, Void> statementInstrumenter =
        createStatementInstrumenter(openTelemetry);
    Instrumenter<DbRequest, Void> transactionInstrumenter =
        createTransactionInstrumenter(openTelemetry, true);
    DbInfo dbInfo = getDbInfo();
    return new OpenTelemetryConnection(
        new TestConnection(executedSql::add),
        dbInfo,
        statementInstrumenter,
        transactionInstrumenter,
        false,
        false);
  }
}
