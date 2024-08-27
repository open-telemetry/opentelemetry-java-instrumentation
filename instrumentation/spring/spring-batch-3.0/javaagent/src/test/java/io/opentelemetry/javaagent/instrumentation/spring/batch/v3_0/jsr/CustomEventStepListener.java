/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.jsr;

import io.opentelemetry.api.trace.Span;
import javax.batch.api.listener.StepListener;

class CustomEventStepListener implements StepListener {
  @Override
  public void beforeStep() {
    Span.current().addEvent("step.before");
  }

  @Override
  public void afterStep() {
    Span.current().addEvent("step.after");
  }
}
