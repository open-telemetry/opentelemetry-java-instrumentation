/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.logback.v1_0

import io.opentelemetry.instrumentation.logback.mdc.v1_0.AbstractLogbackTest
import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.javaagent.instrumentation.logback.mdc.v1_0.LoggingEventInstrumentation

class LogbackTest extends AbstractLogbackTest implements AgentTestTrait {
  @Override
  void setBaggageFlag() {
    LoggingEventInstrumentation.GetMdcAdvice.addBaggage = false
  }
}
