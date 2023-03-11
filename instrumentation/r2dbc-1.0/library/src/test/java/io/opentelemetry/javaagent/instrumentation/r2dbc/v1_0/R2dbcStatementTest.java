/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.r2dbc.v1_0;

import io.opentelemetry.instrumentation.reactor.v3_1.ContextPropagationOperator;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;

class R2dbcStatementTest extends AbstractR2dbcStatementTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  private final ContextPropagationOperator tracingOperator = ContextPropagationOperator.create();

  @Override
  protected InstrumentationExtension getTesting() {
    return testing;
  }

  @BeforeAll
  void setup() {
    tracingOperator.registerOnEachOperator();
  }

  @AfterAll
  void stop() {
    tracingOperator.resetOnEachOperator();
  }

  @Override
  protected ConnectionFactory createProxyConnectionFactory(
      ConnectionFactoryOptions connectionFactoryOptions) {
    return R2dbcTelemetry.create(testing.getOpenTelemetry())
        .wrapConnectionFactory(
            super.createProxyConnectionFactory(connectionFactoryOptions), connectionFactoryOptions);
  }
}
