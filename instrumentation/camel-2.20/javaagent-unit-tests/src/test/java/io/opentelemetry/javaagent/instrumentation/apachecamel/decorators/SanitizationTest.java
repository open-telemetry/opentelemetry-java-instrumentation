/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachecamel.decorators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlStatementInfo;
import java.util.stream.Stream;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SanitizationTest {

  @ParameterizedTest
  @MethodSource("sanitizeCqlArgs")
  void sanitizeCql(String original, String expectedStatement, String expectedSummary) {
    DbSpanDecorator decorator = new DbSpanDecorator("cql", "");

    Exchange exchange = mock(Exchange.class);
    Message message = mock(Message.class);
    when(message.getHeader("CamelCqlQuery")).thenReturn(original);
    when(exchange.getIn()).thenReturn(message);

    SqlStatementInfo info = decorator.getSqlStatementInfo(exchange);
    assertThat(info.getQueryText()).isEqualTo(expectedStatement);
    assertThat(info.getQuerySummary()).isEqualTo(expectedSummary);
  }

  static Stream<Arguments> sanitizeCqlArgs() {
    return Stream.of(
        Arguments.of(
            "SELECT * FROM users WHERE field>=-1234",
            "SELECT * FROM users WHERE field>=?",
            "SELECT users"),
        Arguments.of(
            "SELECT name, phone FROM contact WHERE state = 'NY'",
            "SELECT name, phone FROM contact WHERE state = ?",
            "SELECT contact"),
        Arguments.of(
            "SELECT * FROM col WHERE tag='Something'",
            "SELECT * FROM col WHERE tag=?",
            "SELECT col"));
  }

  @ParameterizedTest
  @MethodSource("sanitizeJdbcArgs")
  void sanitizeJdbc(String original, String expectedStatement, String expectedSummary) {
    DbSpanDecorator decorator = new DbSpanDecorator("jdbc", "");

    Exchange exchange = mock(Exchange.class);
    Message message = mock(Message.class);
    when(message.getBody()).thenReturn(original);
    when(exchange.getIn()).thenReturn(message);

    SqlStatementInfo info = decorator.getSqlStatementInfo(exchange);
    assertThat(info.getQueryText()).isEqualTo(expectedStatement);
    assertThat(info.getQuerySummary()).isEqualTo(expectedSummary);
  }

  static Stream<Arguments> sanitizeJdbcArgs() {
    return Stream.of(
        Arguments.of("SELECT 3", "SELECT ?", "SELECT"),
        Arguments.of(
            "SELECT * FROM TABLE WHERE FIELD = 1234",
            "SELECT * FROM TABLE WHERE FIELD = ?",
            "SELECT TABLE"),
        Arguments.of(
            "SELECT * FROM TABLE WHERE FIELD<-1234",
            "SELECT * FROM TABLE WHERE FIELD<?",
            "SELECT TABLE"),
        Arguments.of(
            "SELECT col1 AS col2 FROM users WHERE field=1234",
            "SELECT col1 AS col2 FROM users WHERE field=?",
            "SELECT users"));
  }

  @ParameterizedTest
  @MethodSource("sanitizeSqlArgs")
  void sanitizeSql(String original, String expectedStatement, String expectedSummary) {
    DbSpanDecorator decorator = new DbSpanDecorator("sql", "");

    Exchange exchange = mock(Exchange.class);
    Message message = mock(Message.class);
    when(message.getHeader("CamelSqlQuery")).thenReturn(original);
    when(exchange.getIn()).thenReturn(message);

    SqlStatementInfo info = decorator.getSqlStatementInfo(exchange);
    assertThat(info.getQueryText()).isEqualTo(expectedStatement);
    assertThat(info.getQuerySummary()).isEqualTo(expectedSummary);
  }

  static Stream<Arguments> sanitizeSqlArgs() {
    return Stream.of(
        Arguments.of(
            "SELECT * FROM table WHERE col1=1234 AND col2>3",
            "SELECT * FROM table WHERE col1=? AND col2>?",
            "SELECT table"),
        Arguments.of("UPDATE table SET col=12", "UPDATE table SET col=?", "UPDATE table"),
        Arguments.of(
            "insert into table where col=321", "insert into table where col=?", "INSERT table"));
  }
}
