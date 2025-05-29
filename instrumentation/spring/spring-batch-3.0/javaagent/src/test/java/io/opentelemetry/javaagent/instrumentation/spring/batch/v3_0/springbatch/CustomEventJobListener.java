/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.springbatch;

import io.opentelemetry.api.trace.Span;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;

public class CustomEventJobListener implements JobExecutionListener {
  @Override
  public void beforeJob(JobExecution jobExecution) {
    Span.current().addEvent("job.before");
  }

  @Override
  public void afterJob(JobExecution jobExecution) {
    Span.current().addEvent("job.after");
  }
}
