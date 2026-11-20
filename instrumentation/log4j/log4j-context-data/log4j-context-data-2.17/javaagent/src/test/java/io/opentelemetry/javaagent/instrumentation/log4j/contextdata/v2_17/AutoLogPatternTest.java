/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.log4j.contextdata.v2_17;

import io.opentelemetry.instrumentation.log4j.contextdata.LogPatternTest;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.junit.jupiter.api.extension.RegisterExtension;

class AutoLogPatternTest extends LogPatternTest {
  @RegisterExtension
  static final InstrumentationExtension agent = AgentInstrumentationExtension.create();

  @Override
  protected InstrumentationExtension testing() {
    return agent;
  }
}
