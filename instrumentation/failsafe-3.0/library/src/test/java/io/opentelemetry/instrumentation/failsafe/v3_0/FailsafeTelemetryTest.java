/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.failsafe.v3_0;

import dev.failsafe.CircuitBreaker;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import org.junit.jupiter.api.extension.RegisterExtension;

final class FailsafeTelemetryTest extends AbstractFailsafeTest {
  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  @Override
  protected CircuitBreaker<Object> configure(CircuitBreaker<Object> circuitBreaker) {
    return FailsafeTelemetry.create(testing.getOpenTelemetry())
        .createCircuitBreaker(circuitBreaker, "testing");
  }
}
