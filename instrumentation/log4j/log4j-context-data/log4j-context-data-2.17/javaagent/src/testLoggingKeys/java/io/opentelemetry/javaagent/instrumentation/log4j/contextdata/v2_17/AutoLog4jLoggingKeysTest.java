/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.log4j.contextdata.v2_17;

import io.opentelemetry.instrumentation.log4j.contextdata.Log4j2LoggingKeysTest;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.junit.jupiter.api.extension.RegisterExtension;

class AutoLog4jLoggingKeysTest extends Log4j2LoggingKeysTest {
  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Override
  public InstrumentationExtension getInstrumentationExtension() {
    return testing;
  }
}
