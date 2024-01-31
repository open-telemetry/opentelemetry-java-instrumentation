/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.oshi;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class ProcessMetricsTest extends AbstractProcessMetricsTest {

  @RegisterExtension
  public static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Override
  protected List<AutoCloseable> registerMetrics() {
    return ProcessMetrics.registerObservers(GlobalOpenTelemetry.get());
  }

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  @Test
  void closeObservables() {
    List<AutoCloseable> closeables = registerMetrics();
    Assertions.assertThat(closeables).as("List of observables").isNotEmpty();
  }
}
