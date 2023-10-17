/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.datasource;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.semconv.SemanticAttributes;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class JdbcTelemetryTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Test
  void buildWithDefaults() throws SQLException {
    JdbcTelemetry telemetry = JdbcTelemetry.builder(testing.getOpenTelemetry()).build();
    DataSource dataSource = telemetry.wrap(new TestDataSource());

    testing.runWithSpan(
        "parent", () -> dataSource.getConnection().createStatement().execute("SELECT 1;"));

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent"),
                span -> span.hasName("TestDataSource.getConnection"),
                span ->
                    span.hasName("SELECT dbname")
                        .hasAttribute(equalTo(SemanticAttributes.DB_STATEMENT, "SELECT ?;"))));
  }

  @Test
  void buildWithAllInstrumentersDisabled() throws SQLException {
    JdbcTelemetry telemetry =
        JdbcTelemetry.builder(testing.getOpenTelemetry())
            .setDataSourceInstrumenterEnabled(false)
            .setStatementInstrumenterEnabled(false)
            .build();

    DataSource dataSource = telemetry.wrap(new TestDataSource());

    testing.runWithSpan(
        "parent", () -> dataSource.getConnection().createStatement().execute("SELECT 1;"));

    testing.waitAndAssertTraces(
        trace -> trace.hasSpansSatisfyingExactly(span -> span.hasName("parent")));
  }

  @Test
  void buildWithDataSourceInstrumenterDisabled() throws SQLException {
    JdbcTelemetry telemetry =
        JdbcTelemetry.builder(testing.getOpenTelemetry())
            .setDataSourceInstrumenterEnabled(false)
            .build();

    DataSource dataSource = telemetry.wrap(new TestDataSource());

    testing.runWithSpan(
        "parent", () -> dataSource.getConnection().createStatement().execute("SELECT 1;"));

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent"), span -> span.hasName("SELECT dbname")));
  }

  @Test
  void buildWithStatementInstrumenterDisabled() throws SQLException {
    JdbcTelemetry telemetry =
        JdbcTelemetry.builder(testing.getOpenTelemetry())
            .setStatementInstrumenterEnabled(false)
            .build();

    DataSource dataSource = telemetry.wrap(new TestDataSource());

    testing.runWithSpan(
        "parent", () -> dataSource.getConnection().createStatement().execute("SELECT 1;"));

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent"),
                span -> span.hasName("TestDataSource.getConnection")));
  }

  @Test
  void buildWithSanitizationDisabled() throws SQLException {
    JdbcTelemetry telemetry =
        JdbcTelemetry.builder(testing.getOpenTelemetry())
            .setStatementSanitizationEnabled(false)
            .build();

    DataSource dataSource = telemetry.wrap(new TestDataSource());

    testing.runWithSpan(
        "parent", () -> dataSource.getConnection().createStatement().execute("SELECT 1;"));

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent"),
                span -> span.hasName("TestDataSource.getConnection"),
                span ->
                    span.hasName("SELECT dbname")
                        .hasAttribute(equalTo(SemanticAttributes.DB_STATEMENT, "SELECT 1;"))));
  }
}
