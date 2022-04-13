/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.micrometer.v1_5;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;

@Deprecated
class TimerTest extends AbstractTimerTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  static MeterRegistry otelMeterRegistry;

  @BeforeAll
  public static void setUpRegistry() {
    otelMeterRegistry = OpenTelemetryMeterRegistry.create(testing.getOpenTelemetry());
    Metrics.addRegistry(otelMeterRegistry);
  }

  @AfterAll
  public static void tearDownRegistry() {
    Metrics.removeRegistry(otelMeterRegistry);
  }

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }
}
