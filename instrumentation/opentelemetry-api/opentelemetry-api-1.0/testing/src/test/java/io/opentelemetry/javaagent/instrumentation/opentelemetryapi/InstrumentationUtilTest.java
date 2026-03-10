/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.internal.InstrumentationUtil;
import io.opentelemetry.context.Context;
import org.junit.jupiter.api.Test;

class InstrumentationUtilTest {

  @Test
  void instrumentationSuppression() {
    Context[] contexts = new Context[1];
    InstrumentationUtil.suppressInstrumentation(() -> contexts[0] = Context.current());

    assertThat(InstrumentationUtil.shouldSuppressInstrumentation(contexts[0])).isTrue();
    assertThat(TestClass.shouldSuppressInstrumentation(contexts[0])).isTrue();
  }
}
