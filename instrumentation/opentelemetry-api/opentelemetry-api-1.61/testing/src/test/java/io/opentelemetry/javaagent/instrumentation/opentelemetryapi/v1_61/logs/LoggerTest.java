/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_61.logs;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class LoggerTest {

  @RegisterExtension
  static final AgentInstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  void isEnabled() {
    Logger disabledLogger = testing.getOpenTelemetry().getLogsBridge().get("disabled-logger");
    Logger enabledLogger = testing.getOpenTelemetry().getLogsBridge().get("enabled-logger");
    testEnabled(disabledLogger, false);
    testEnabled(enabledLogger, true);
  }

  private static void testEnabled(Logger logger, boolean expected) {
    assertThat(logger.isEnabled(Severity.INFO)).isEqualTo(expected);
    assertThat(logger.isEnabled(Severity.INFO, Context.current())).isEqualTo(expected);
  }
}
