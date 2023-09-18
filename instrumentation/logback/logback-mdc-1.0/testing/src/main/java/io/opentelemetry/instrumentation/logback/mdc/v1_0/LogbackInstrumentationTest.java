/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.logback.mdc.v1_0;

import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;

public interface LogbackInstrumentationTest {
  InstrumentationExtension agentTesting = AgentInstrumentationExtension.create();

  default InstrumentationExtension getInstrumentationExtension() {
    return agentTesting;
  }
}
