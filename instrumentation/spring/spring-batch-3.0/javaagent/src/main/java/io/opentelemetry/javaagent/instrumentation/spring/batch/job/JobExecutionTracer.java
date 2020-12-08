/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.batch.job;

import static io.opentelemetry.api.trace.Span.Kind.INTERNAL;

import io.opentelemetry.api.trace.Span;
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
    Span span = startSpan("BatchJob " + jobName, INTERNAL);
    return Context.current().with(span);
  }

  public void end(Context context) {
    end(Span.fromContext(context));
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.spring-batch";
  }
}
