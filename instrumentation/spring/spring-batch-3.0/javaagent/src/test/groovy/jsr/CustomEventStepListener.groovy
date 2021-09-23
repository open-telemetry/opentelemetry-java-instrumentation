/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package jsr

import io.opentelemetry.api.trace.Span

import javax.batch.api.listener.StepListener

class CustomEventStepListener implements StepListener {
  @Override
  void beforeStep() throws Exception {
    Span.current().addEvent("step.before")
  }

  @Override
  void afterStep() throws Exception {
    Span.current().addEvent("step.after")
  }
}
