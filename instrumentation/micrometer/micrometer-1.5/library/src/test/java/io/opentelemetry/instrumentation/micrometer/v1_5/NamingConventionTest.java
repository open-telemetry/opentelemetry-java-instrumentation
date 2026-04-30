/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.micrometer.v1_5;

import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import org.junit.jupiter.api.extension.RegisterExtension;

class NamingConventionTest extends AbstractNamingConventionTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @RegisterExtension
  static final MicrometerTestingExtension micrometerExtension =
      new MicrometerTestingExtension(testing) {
        @Override
        void configureMeterRegistry(MeterRegistry registry) {
          registry.config().namingConvention(AbstractNamingConventionTest.namingConvention());
        }
      };

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }
}
