/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.micrometer1shim;

import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import org.junit.jupiter.api.extension.RegisterExtension;

public class NamingConventionTest extends AbstractNamingConventionTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @RegisterExtension
  static final MicrometerTestingExtension micrometerExtension =
      new MicrometerTestingExtension(testing) {
        @Override
        MeterRegistry configureMeterRegistry(MeterRegistry registry) {
          registry.config().namingConvention(AbstractNamingConventionTest.namingConvention());
          return registry;
        }
      };

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }
}
