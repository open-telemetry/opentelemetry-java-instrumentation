/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.jdbc;

import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStable;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_STATEMENT;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Collections;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class JdbcInstrumentationAutoConfigurationTest {

  @RegisterExtension
  static final LibraryInstrumentationExtension testing = LibraryInstrumentationExtension.create();

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withBean(
              ConfigProperties.class,
              () -> DefaultConfigProperties.createFromMap(Collections.emptyMap()))
          .withConfiguration(
              AutoConfigurations.of(
                  JdbcInstrumentationAutoConfiguration.class, DataSourceAutoConfiguration.class))
          .withBean("openTelemetry", OpenTelemetry.class, testing::getOpenTelemetry);

  @SuppressWarnings("deprecation") // using deprecated semconv
  @Test
  void statementSanitizerEnabledByDefault() {
    runner.run(
        context -> {
          DataSource dataSource = context.getBean(DataSource.class);

          assertThat(AopUtils.isAopProxy(dataSource)).isTrue();
          assertThat(dataSource.getClass().getSimpleName()).isNotEqualTo("HikariDataSource");
          // unwrap the instrumented data source to get the original data source
          Object original = ((Advised) dataSource).getTargetSource().getTarget();
          assertThat(AopUtils.isAopProxy(original)).isFalse();
          assertThat(original.getClass().getSimpleName()).isEqualTo("HikariDataSource");

          try (Connection connection = dataSource.getConnection()) {
            try (Statement statement = connection.createStatement()) {
              statement.execute("SELECT 1");
            }
          }

          testing.waitAndAssertTraces(
              trace ->
                  trace.hasSpansSatisfyingExactly(
                      span -> span.hasAttribute(maybeStable(DB_STATEMENT), "SELECT ?")));
        });
  }
}
