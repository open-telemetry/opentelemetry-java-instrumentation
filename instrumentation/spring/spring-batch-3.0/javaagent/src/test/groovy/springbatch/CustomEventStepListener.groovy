/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package springbatch

import io.opentelemetry.api.trace.Span
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.StepExecution
import org.springframework.batch.core.StepExecutionListener

class CustomEventStepListener implements StepExecutionListener {
  @Override
  void beforeStep(StepExecution stepExecution) {
    Span.current().addEvent("step.before")
  }

  @Override
  ExitStatus afterStep(StepExecution stepExecution) {
    Span.current().addEvent("step.after")
    return null
  }
}
