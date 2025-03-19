/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.r2dbc;

import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStable;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_STATEMENT;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.r2dbc.R2dbcAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.r2dbc.core.DatabaseClient;

class R2DbcInstrumentationAutoConfigurationTest {

  @RegisterExtension
  static final LibraryInstrumentationExtension testing = LibraryInstrumentationExtension.create();

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withBean(
              ConfigProperties.class,
              () -> DefaultConfigProperties.createFromMap(Collections.emptyMap()))
          .withConfiguration(
              AutoConfigurations.of(
                  R2dbcInstrumentationAutoConfiguration.class, R2dbcAutoConfiguration.class))
          .withBean("openTelemetry", OpenTelemetry.class, testing::getOpenTelemetry);

  @SuppressWarnings("deprecation") // using deprecated semconv
  @Test
  void statementSanitizerEnabledByDefault() {
    runner.run(
        context -> {
          DatabaseClient client = context.getBean(DatabaseClient.class);
          client
              .sql(
                  "CREATE TABLE IF NOT EXISTS player(id INT NOT NULL AUTO_INCREMENT, name VARCHAR(255), age INT, PRIMARY KEY (id))")
              .fetch()
              .all()
              .blockLast();
          client.sql("SELECT * FROM player WHERE id = 1").fetch().all().blockLast();
          testing.waitAndAssertTraces(
              trace ->
                  trace.hasSpansSatisfyingExactly(
                      span ->
                          span.hasAttribute(
                              maybeStable(DB_STATEMENT),
                              "CREATE TABLE IF NOT EXISTS player(id INT NOT NULL AUTO_INCREMENT, name VARCHAR(?), age INT, PRIMARY KEY (id))")),
              trace ->
                  trace.hasSpansSatisfyingExactly(
                      span ->
                          span.hasAttribute(
                              maybeStable(DB_STATEMENT), "SELECT * FROM player WHERE id = ?")));
        });
  }
}
