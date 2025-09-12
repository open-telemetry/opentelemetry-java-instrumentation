/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jdbc.test;

import static io.opentelemetry.api.trace.SpanKind.INTERNAL;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.incubating.CodeIncubatingAttributes.CODE_FUNCTION;
import static io.opentelemetry.semconv.incubating.CodeIncubatingAttributes.CODE_NAMESPACE;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_NAME;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;
import static org.assertj.core.api.Assertions.assertThat;

import com.alibaba.druid.pool.DruidDataSource;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

@SuppressWarnings("deprecation") // using deprecated semconv
class DataSourceInstrumentationTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private DataSource dataSource;

  @BeforeEach
  void setUp() {
    DruidDataSource druidDataSource = new DruidDataSource();
    druidDataSource.setUrl("jdbc:h2:mem:test");
    druidDataSource.setDriverClassName("org.h2.Driver");
    druidDataSource.setUsername("sa");
    druidDataSource.setPassword("");
    druidDataSource.setMaxActive(1);
    this.dataSource = druidDataSource;
  }

  @AfterEach
  void tearDown() {
    if (dataSource instanceof DruidDataSource) {
      ((DruidDataSource) dataSource).close();
    }
  }

  @Test
  void testDruidDataSourceGetConnection() throws SQLException {
    testing.runWithSpan(
        "parent",
        () -> {
          try (Connection connection = dataSource.getConnection()) {
            return null;
          } catch (SQLException e) {
            throw new RuntimeException(e);
          }
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(INTERNAL).hasNoParent(),
                span ->
                    span.hasName("DruidDataSource.getConnection")
                        .hasKind(INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfying(
                            equalTo(CODE_NAMESPACE, "com.alibaba.druid.pool.DruidDataSource"),
                            equalTo(CODE_FUNCTION, "getConnection"),
                            equalTo(DB_SYSTEM, "h2"),
                            equalTo(DB_NAME, "test"))));

    List<SpanData> spans = testing.spans();
    List<SpanData> dataSourceSpans =
        spans.stream()
            .filter(span -> span.getName().equals("DruidDataSource.getConnection"))
            .collect(Collectors.toList());

    assertThat(dataSourceSpans)
        .as("Should have exactly one DruidDataSource.getConnection span, not duplicates")
        .hasSize(1);
  }
}
