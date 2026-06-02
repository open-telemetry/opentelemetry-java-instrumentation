/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rxjava.v2_0;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;

class RxJava2SubscriptionTest extends AbstractRxJava2SubscriptionTest {
  @RegisterExtension
  private static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  private static final TracingAssembly tracingAssembly = TracingAssembly.create();

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  @BeforeAll
  static void setup() {
    tracingAssembly.enable();
  }

  @AfterAll
  static void cleanup() {
    tracingAssembly.disable();
  }
}
