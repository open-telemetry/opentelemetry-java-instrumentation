/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package springbatch

import io.opentelemetry.api.trace.Span
import org.springframework.batch.core.JobExecution
import org.springframework.batch.core.JobExecutionListener

class CustomEventJobListener implements JobExecutionListener {
  @Override
  void beforeJob(JobExecution jobExecution) {
    Span.current().addEvent("job.before")
  }

  @Override
  void afterJob(JobExecution jobExecution) {
    Span.current().addEvent("job.after")
  }
}
