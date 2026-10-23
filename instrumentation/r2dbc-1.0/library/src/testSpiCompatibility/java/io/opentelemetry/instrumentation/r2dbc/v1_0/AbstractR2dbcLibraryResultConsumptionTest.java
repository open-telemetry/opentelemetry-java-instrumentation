/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.r2dbc.v1_0;

import io.opentelemetry.instrumentation.reactor.v3_1.ContextPropagationOperator;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;

abstract class AbstractR2dbcLibraryResultConsumptionTest
    extends AbstractR2dbcResultConsumptionTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  private static final ContextPropagationOperator tracingOperator =
      ContextPropagationOperator.create();

  @BeforeAll
  static void setup() {
    tracingOperator.registerOnEachOperator();
  }

  @AfterAll
  static void stop() {
    tracingOperator.resetOnEachOperator();
  }

  @Override
  protected InstrumentationExtension getTesting() {
    return testing;
  }

  @Override
  protected ConnectionFactory createProxyConnectionFactory(ConnectionFactoryOptions options) {
    return R2dbcTelemetry.create(testing.getOpenTelemetry())
        .wrapConnectionFactory(ConnectionFactories.find(options), options);
  }
}
