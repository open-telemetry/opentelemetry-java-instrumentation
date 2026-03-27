/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.oshi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class SystemMetricsTest extends AbstractSystemMetricsTest {

  @RegisterExtension
  public static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  private static List<AutoCloseable> observables;

  @BeforeAll
  static void setUp() {
    observables = SystemMetrics.registerObservers(GlobalOpenTelemetry.get());
  }

  @AfterAll
  static void tearDown() {
    assertAll(observables.stream().map(observable -> observable::close));
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
