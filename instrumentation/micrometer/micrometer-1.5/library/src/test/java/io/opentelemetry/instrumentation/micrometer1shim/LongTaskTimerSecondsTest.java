/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.micrometer1shim;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.extension.RegisterExtension;

class LongTaskTimerSecondsTest extends AbstractLongTaskTimerSecondsTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @RegisterExtension
  static final MicrometerTestingExtension micrometerExtension =
      new MicrometerTestingExtension(testing) {
        @Override
        OpenTelemetryMeterRegistryBuilder configureOtelRegistry(
            OpenTelemetryMeterRegistryBuilder registry) {
          return registry.setBaseTimeUnit(TimeUnit.SECONDS);
        }
      };

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }
}
