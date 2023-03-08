/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.r2dbc.v1_0;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import org.junit.jupiter.api.extension.RegisterExtension;

class R2dbcStatementTest extends AbstractR2dbcStatementTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Override
  protected InstrumentationExtension getTesting() {
    return testing;
  }

  @Override
  ConnectionFactory createProxyConnectionFactory(
      ConnectionFactoryOptions connectionFactoryOptions) {
    ConnectionFactory originalFactory = ConnectionFactories.find(connectionFactoryOptions);
    return R2dbcTelemetry.builder(testing.getOpenTelemetry())
        .setOriginalConnectionFactory(originalFactory)
        .setConnectionFactoryOptions(connectionFactoryOptions)
        .build()
        .getConnectionFactory();
  }
}
