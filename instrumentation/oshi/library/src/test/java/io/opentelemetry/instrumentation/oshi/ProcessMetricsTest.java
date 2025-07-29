/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.oshi;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class ProcessMetricsTest extends AbstractProcessMetricsTest {

  @RegisterExtension
  public static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  private static List<AutoCloseable> observables;

  @BeforeAll
  static void setUp() {
    observables = ProcessMetrics.registerObservers(GlobalOpenTelemetry.get());
  }

  @AfterAll
  static void tearDown() {
    for (AutoCloseable observable : observables) {
      try {
        observable.close();
      } catch (Exception e) {
        // ignore
      }
    }
  }

  @Override
  protected void registerMetrics() {}

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  @Test
  void verifyObservablesAreNotEmpty() {
    assertThat(observables).as("List of observables").isNotEmpty();
  }
}
