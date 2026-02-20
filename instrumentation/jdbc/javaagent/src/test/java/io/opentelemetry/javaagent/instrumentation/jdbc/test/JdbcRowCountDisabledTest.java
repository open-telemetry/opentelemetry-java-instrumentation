/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jdbc.test;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class JdbcRowCountDisabledTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static Connection connection;

  @BeforeAll
  static void setUp() throws Exception {
    connection = DriverManager.getConnection("jdbc:h2:mem:testDisabled", "sa", "");
    try (Statement stmt = connection.createStatement()) {
      stmt.execute("CREATE TABLE test_table (id INT PRIMARY KEY, name VARCHAR(50))");
      stmt.execute("INSERT INTO test_table VALUES (1, 'row1')");
      stmt.execute("INSERT INTO test_table VALUES (2, 'row2')");
      stmt.execute("INSERT INTO test_table VALUES (3, 'row3')");
    }
  }

  @AfterAll
  static void tearDown() throws Exception {
    if (connection != null) {
      connection.close();
    }
  }

  @Test
  void rowCountDisabledByDefault() throws Exception {
    testing.runWithSpan(
        "parent",
        () -> {
          try (Statement stmt = connection.createStatement();
              ResultSet rs = stmt.executeQuery("SELECT * FROM test_table")) {
            while (rs.next()) {
              // consume result set
            }
          }
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent"),
                span ->
                    span.hasName(
                            SemconvStability.emitStableDatabaseSemconv()
                                ? "SELECT test_table"
                                : "SELECT testdisabled.test_table")
                        .satisfies(
                            s ->
                                assertThat(
                                        s.getAttributes()
                                            .get(AttributeKey.longKey("db.response.returned_rows")))
                                    .isNull())));
  }
}
