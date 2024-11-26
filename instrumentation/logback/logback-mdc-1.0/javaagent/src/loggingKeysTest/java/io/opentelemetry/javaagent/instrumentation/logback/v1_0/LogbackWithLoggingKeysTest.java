/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.logback.v1_0;

import io.opentelemetry.instrumentation.logback.mdc.v1_0.AbstractLogbackTest;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.junit.jupiter.api.extension.RegisterExtension;

class LogbackWithLoggingKeysTest extends AbstractLogbackTest {

  @RegisterExtension
  static InstrumentationExtension agentTesting = AgentInstrumentationExtension.create();

  @Override
  public InstrumentationExtension getInstrumentationExtension() {
    return agentTesting;
  }

  @Override
  protected boolean expectLoggingKeys() {
    return true;
  }
}
