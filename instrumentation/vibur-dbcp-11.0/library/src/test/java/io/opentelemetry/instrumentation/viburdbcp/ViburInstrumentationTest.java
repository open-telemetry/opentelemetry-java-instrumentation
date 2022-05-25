/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.viburdbcp;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.vibur.dbcp.ViburDBCPDataSource;

class ViburInstrumentationTest extends AbstractViburInstrumentationTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  private ViburTelemetry telemetry;

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  @Override
  protected void configure(ViburDBCPDataSource viburDataSource) {
    telemetry = ViburTelemetry.create(testing().getOpenTelemetry());
    telemetry.registerMetrics(viburDataSource);
  }

  @Override
  protected void shutdown(ViburDBCPDataSource viburDataSource) {
    telemetry.unregisterMetrics(viburDataSource);
  }
}
