/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.springbatch;

import io.opentelemetry.api.trace.Span;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;

public class CustomEventStepListener implements StepExecutionListener {
  @Override
  public void beforeStep(StepExecution stepExecution) {
    Span.current().addEvent("step.before");
  }

  @Override
  public ExitStatus afterStep(StepExecution stepExecution) {
    Span.current().addEvent("step.after");
    return null;
  }
}
