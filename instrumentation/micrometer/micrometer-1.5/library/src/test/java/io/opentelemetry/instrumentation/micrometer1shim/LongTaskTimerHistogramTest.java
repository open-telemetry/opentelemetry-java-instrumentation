/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.micrometer1shim;

import io.micrometer.core.instrument.MockClock;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import org.junit.jupiter.api.extension.RegisterExtension;

class LongTaskTimerHistogramTest extends AbstractLongTaskTimerHistogramTest {

  private static final MockClock clock = new MockClock();

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @RegisterExtension
  static final MicrometerTestingExtension micrometerExtension =
      new MicrometerTestingExtension(testing) {
        @Override
        OpenTelemetryMeterRegistryBuilder configureOtelRegistry(
            OpenTelemetryMeterRegistryBuilder registry) {
          return registry.setClock(clock);
        }
      };

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  @Override
  protected MockClock clock() {
    return clock;
  }
}
