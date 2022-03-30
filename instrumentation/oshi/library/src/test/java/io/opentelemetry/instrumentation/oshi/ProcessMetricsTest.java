/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.oshi;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import org.junit.jupiter.api.extension.RegisterExtension;

class ProcessMetricsTest extends AbstractProcessMetricsTest {

  @RegisterExtension
  public static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Override
  protected void registerMetrics() {
    ProcessMetrics.registerObservers(GlobalOpenTelemetry.get());
  }

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }
}
