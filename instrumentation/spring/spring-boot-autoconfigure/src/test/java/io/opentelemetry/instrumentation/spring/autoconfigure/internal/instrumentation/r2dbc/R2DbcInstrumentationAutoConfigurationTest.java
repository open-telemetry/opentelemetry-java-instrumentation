/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.r2dbc;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.logs.LoggerProvider;
import io.opentelemetry.api.metrics.MeterProvider;
import io.opentelemetry.api.trace.TracerBuilder;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes;
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
          .withBean(
              "openTelemetry",
              OpenTelemetry.class,
              R2DbcInstrumentationAutoConfigurationTest::openTelemetry);

  private static OpenTelemetry openTelemetry() {
    // Wrap OpenTelemetry instance so it wouldn't implement Closeable. Spring closing the
    // OpenTelemetry instance used here will break the following tests that use the same
    // OpenTelemetry instance.
    OpenTelemetry delegate = testing.getOpenTelemetry();
    return new OpenTelemetry() {
      @Override
      public TracerProvider getTracerProvider() {
        return delegate.getTracerProvider();
      }

      @Override
      public MeterProvider getMeterProvider() {
        return delegate.getMeterProvider();
      }

      @Override
      public LoggerProvider getLogsBridge() {
        return delegate.getLogsBridge();
      }

      @Override
      public ContextPropagators getPropagators() {
        return delegate.getPropagators();
      }

      @Override
      public TracerBuilder tracerBuilder(String instrumentationScopeName) {
        return delegate.tracerBuilder(instrumentationScopeName);
      }
    };
  }

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
                              DbIncubatingAttributes.DB_STATEMENT,
                              "CREATE TABLE IF NOT EXISTS player(id INT NOT NULL AUTO_INCREMENT, name VARCHAR(?), age INT, PRIMARY KEY (id))")),
              trace ->
                  trace.hasSpansSatisfyingExactly(
                      span ->
                          span.hasAttribute(
                              DbIncubatingAttributes.DB_STATEMENT,
                              "SELECT * FROM player WHERE id = ?")));
        });
  }
}
