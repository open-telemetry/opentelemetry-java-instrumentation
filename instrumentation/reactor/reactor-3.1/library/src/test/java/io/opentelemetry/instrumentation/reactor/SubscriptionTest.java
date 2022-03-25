/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.reactor;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;

class SubscriptionTest extends AbstractSubscriptionTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  private final ContextPropagationOperator tracingOperator = ContextPropagationOperator.create();

  SubscriptionTest() {
    super(testing);
  }

  @BeforeAll
  void setUp() {
    tracingOperator.registerOnEachOperator();
  }

  @AfterAll
  void tearDown() {
    tracingOperator.resetOnEachOperator();
  }
}
