/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.batch.step;

import static io.opentelemetry.javaagent.instrumentation.spring.batch.step.StepExecutionTracer.tracer;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.instrumentation.api.ContextStore;
import io.opentelemetry.javaagent.instrumentation.spring.batch.ContextAndScope;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.core.Ordered;

public class TracingStepExecutionListener implements StepExecutionListener, Ordered {
  private final ContextStore<StepExecution, ContextAndScope> executionContextStore;

  public TracingStepExecutionListener(
      ContextStore<StepExecution, ContextAndScope> executionContextStore) {
    this.executionContextStore = executionContextStore;
  }

  @Override
  public void beforeStep(StepExecution stepExecution) {
    Context context = tracer().startSpan(stepExecution);
    // beforeStep & afterStep always execute on the same thread
    Scope scope = context.makeCurrent();
    executionContextStore.put(stepExecution, new ContextAndScope(context, scope));
  }

  @Override
  public ExitStatus afterStep(StepExecution stepExecution) {
    ContextAndScope contextAndScope = executionContextStore.get(stepExecution);
    if (contextAndScope != null) {
      executionContextStore.put(stepExecution, null);
      contextAndScope.closeScope();
      tracer().end(contextAndScope.getContext());
    }
    return null;
  }

  @Override
  public int getOrder() {
    return HIGHEST_PRECEDENCE;
  }

  // equals() and hashCode() methods guarantee that only one instance of
  // TracingStepExecutionListener will be present in an ordered set of listeners

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    return o != null && getClass() == o.getClass();
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
