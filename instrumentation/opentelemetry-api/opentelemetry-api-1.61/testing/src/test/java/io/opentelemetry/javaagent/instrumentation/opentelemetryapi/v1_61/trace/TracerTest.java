/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_61.trace;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class TracerTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  void isEnabled() {
    Tracer disabledTracer = testing.getOpenTelemetry().getTracer("disabled-tracer");
    Tracer enabledTracer = testing.getOpenTelemetry().getTracer("enabled-tracer");
    testEnabled(disabledTracer, false);
    testEnabled(enabledTracer, true);
  }

  private static void testEnabled(Tracer tracer, boolean expected) {
    assertThat(tracer.isEnabled()).isEqualTo(expected);
  }
}
