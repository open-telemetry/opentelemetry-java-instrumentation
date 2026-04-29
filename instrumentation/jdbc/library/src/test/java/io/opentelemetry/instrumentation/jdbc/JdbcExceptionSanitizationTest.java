/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_MESSAGE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_TYPE;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import io.opentelemetry.instrumentation.jdbc.datasource.JdbcTelemetry;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class JdbcExceptionSanitizationTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  private static final String SENSITIVE_SQL = "SELECT * FROM users WHERE password = 'mysecret'";

  @Test
  void exceptionMessageSanitizedWhenQuerySanitizationEnabled() throws Exception {
    JdbcTelemetry telemetry =
        JdbcTelemetry.builder(testing.getOpenTelemetry()).setQuerySanitizationEnabled(true).build();

    Statement mockStatement = mock(Statement.class);
    doAnswer(
            invoc -> {
              throw new SQLException("Error in query: " + invoc.getArgument(0), "42P01", 1054);
            })
        .when(mockStatement)
        .executeQuery(anyString());

    Connection conn =
        ConnectionWrapper.wrap(
            new TestConnection() {
              @Override
              public Statement createStatement() {
                return mockStatement;
              }
            },
            telemetry);

    assertThatThrownBy(() -> conn.createStatement().executeQuery(SENSITIVE_SQL))
        .isInstanceOf(SQLException.class);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasEventsSatisfyingExactly(
                        event ->
                            event
                                .hasName("exception")
                                .hasAttributesSatisfying(
                                    equalTo(EXCEPTION_TYPE, "java.sql.SQLException"),
                                    satisfies(
                                        EXCEPTION_MESSAGE,
                                        msg -> msg.doesNotContain("mysecret"))))));
  }

  @Test
  void exceptionMessageNotSanitizedWhenQuerySanitizationDisabled() throws Exception {
    JdbcTelemetry telemetry =
        JdbcTelemetry.builder(testing.getOpenTelemetry())
            .setQuerySanitizationEnabled(false)
            .build();

    Statement mockStatement = mock(Statement.class);
    doAnswer(
            invoc -> {
              throw new SQLException("Error in query: " + invoc.getArgument(0), "42P01", 1054);
            })
        .when(mockStatement)
        .executeQuery(anyString());

    Connection conn =
        ConnectionWrapper.wrap(
            new TestConnection() {
              @Override
              public Statement createStatement() {
                return mockStatement;
              }
            },
            telemetry);

    assertThatThrownBy(() -> conn.createStatement().executeQuery(SENSITIVE_SQL))
        .isInstanceOf(SQLException.class);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasEventsSatisfyingExactly(
                        event ->
                            event
                                .hasName("exception")
                                .hasAttributesSatisfying(
                                    equalTo(EXCEPTION_TYPE, "java.sql.SQLException"),
                                    satisfies(
                                        EXCEPTION_MESSAGE, msg -> msg.contains("mysecret"))))));
  }

  @Test
  void exceptionTypePreservedAfterSanitization() throws Exception {
    JdbcTelemetry telemetry =
        JdbcTelemetry.builder(testing.getOpenTelemetry()).setQuerySanitizationEnabled(true).build();

    Statement mockStatement = mock(Statement.class);
    doAnswer(
            invoc -> {
              throw new SQLException("Error in query: " + invoc.getArgument(0), "42P01", 1054);
            })
        .when(mockStatement)
        .executeQuery(anyString());

    Connection conn =
        ConnectionWrapper.wrap(
            new TestConnection() {
              @Override
              public Statement createStatement() {
                return mockStatement;
              }
            },
            telemetry);

    assertThatThrownBy(() -> conn.createStatement().executeQuery(SENSITIVE_SQL))
        .isInstanceOf(SQLException.class);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasEventsSatisfyingExactly(
                        event ->
                            event
                                .hasName("exception")
                                .hasAttributesSatisfying(
                                    equalTo(EXCEPTION_TYPE, "java.sql.SQLException")))));
  }

  @Test
  void nonSqlExceptionUnaffected() throws Exception {
    JdbcTelemetry telemetry =
        JdbcTelemetry.builder(testing.getOpenTelemetry()).setQuerySanitizationEnabled(true).build();

    Statement mockStatement = mock(Statement.class);
    doThrow(new RuntimeException("boom")).when(mockStatement).executeQuery(anyString());

    Connection conn =
        ConnectionWrapper.wrap(
            new TestConnection() {
              @Override
              public Statement createStatement() {
                return mockStatement;
              }
            },
            telemetry);

    assertThatThrownBy(() -> conn.createStatement().executeQuery("SELECT 1"))
        .isInstanceOf(RuntimeException.class);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasEventsSatisfyingExactly(
                        event ->
                            event
                                .hasName("exception")
                                .hasAttributesSatisfying(
                                    equalTo(EXCEPTION_TYPE, "java.lang.RuntimeException"),
                                    equalTo(EXCEPTION_MESSAGE, "boom")))));
  }
}
