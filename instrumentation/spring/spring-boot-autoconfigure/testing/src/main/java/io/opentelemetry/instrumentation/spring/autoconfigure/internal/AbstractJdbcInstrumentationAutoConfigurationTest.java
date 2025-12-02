/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal;

/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.InstrumentationConfig;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.properties.ConfigPropertiesBridge;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil;
import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes;
import java.sql.Connection;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

public abstract class AbstractJdbcInstrumentationAutoConfigurationTest {

  protected abstract InstrumentationExtension testing();

  protected abstract AutoConfigurations autoConfigurations();

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withBean(
              InstrumentationConfig.class,
              () -> new ConfigPropertiesBridge(DefaultConfigProperties.createFromMap(emptyMap())))
          .withConfiguration(autoConfigurations())
          .withBean("openTelemetry", OpenTelemetry.class, testing()::getOpenTelemetry);

  @SuppressWarnings("deprecation") // using deprecated semconv
  @Test
  void statementSanitizerEnabledByDefault() {
    contextRunner.run(
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

          testing()
              .waitAndAssertTraces(
                  trace ->
                      trace.hasSpansSatisfyingExactly(
                          span ->
                              span.hasAttribute(
                                  SemconvStabilityUtil.maybeStable(
                                      DbIncubatingAttributes.DB_STATEMENT),
                                  "SELECT ?")));
        });
  }
}
