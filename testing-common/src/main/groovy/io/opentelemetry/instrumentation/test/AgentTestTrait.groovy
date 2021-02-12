/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.test

import io.opentelemetry.instrumentation.testing.AgentTestRunner
import io.opentelemetry.instrumentation.testing.InstrumentationTestRunner

/**
 * A trait which initializes agent tests, including bytecode manipulation and a test span exporter.
 * All agent tests should implement this trait.
 */
trait AgentTestTrait {

  InstrumentationTestRunner testRunner() {
    AgentTestRunner.instance()
  }
}
