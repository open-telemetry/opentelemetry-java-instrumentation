/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.c3p0.v0_9;

import com.mchange.v2.c3p0.PooledDataSource;
import io.opentelemetry.instrumentation.c3p0.AbstractC3p0InstrumentationTest;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;

class C3p0InstrumentationTest extends AbstractC3p0InstrumentationTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  private static C3p0Telemetry telemetry;

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  @BeforeAll
  static void setup() {
    telemetry = C3p0Telemetry.create(testing.getOpenTelemetry());
  }

  @Override
  protected void configure(PooledDataSource dataSource) {
    telemetry.registerMetrics(dataSource);
  }

  @Override
  protected void shutdown(PooledDataSource dataSource) {
    telemetry.unregisterMetrics(dataSource);
  }
}
