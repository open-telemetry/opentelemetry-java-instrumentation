/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.batch.step;

import static io.opentelemetry.api.trace.Span.Kind.INTERNAL;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import org.springframework.batch.core.StepExecution;

public class StepExecutionTracer extends BaseTracer {
  private static final StepExecutionTracer TRACER = new StepExecutionTracer();

  public static StepExecutionTracer tracer() {
    return TRACER;
  }

  public Context startSpan(StepExecution stepExecution) {
    String jobName = stepExecution.getJobExecution().getJobInstance().getJobName();
    String stepName = stepExecution.getStepName();
    Span span = startSpan("BatchJob " + jobName + "." + stepName, INTERNAL);
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
