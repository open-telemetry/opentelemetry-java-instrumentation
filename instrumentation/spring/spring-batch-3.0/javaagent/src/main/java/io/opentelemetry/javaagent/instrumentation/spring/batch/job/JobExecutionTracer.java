/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.batch.job;

import static io.opentelemetry.api.trace.SpanKind.INTERNAL;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import org.springframework.batch.core.JobExecution;

public class JobExecutionTracer extends BaseTracer {
  private static final JobExecutionTracer TRACER = new JobExecutionTracer();

  public static JobExecutionTracer tracer() {
    return TRACER;
  }

  public Context startSpan(JobExecution jobExecution) {
    String jobName = jobExecution.getJobInstance().getJobName();
    return startSpan("BatchJob " + jobName, INTERNAL);
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.spring-batch-3.0";
  }
}
