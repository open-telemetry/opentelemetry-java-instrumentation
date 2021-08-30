/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.batch.step;

import static io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.spring.batch.step.StepExecutionInstrumenter.stepExecutionInstrumenter;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.instrumentation.api.ContextStore;
import io.opentelemetry.javaagent.instrumentation.spring.batch.ContextAndScope;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.core.Ordered;

public final class TracingStepExecutionListener implements StepExecutionListener, Ordered {
  private final ContextStore<StepExecution, ContextAndScope> executionContextStore;

  public TracingStepExecutionListener(
      ContextStore<StepExecution, ContextAndScope> executionContextStore) {
    this.executionContextStore = executionContextStore;
  }

  @Override
  public void beforeStep(StepExecution stepExecution) {
    Context parentContext = currentContext();
    if (!stepExecutionInstrumenter().shouldStart(parentContext, stepExecution)) {
      return;
    }

    Context context = stepExecutionInstrumenter().start(parentContext, stepExecution);
    // beforeStep & afterStep always execute on the same thread
    Scope scope = context.makeCurrent();
    executionContextStore.put(stepExecution, new ContextAndScope(context, scope));
  }

  @Override
  public ExitStatus afterStep(StepExecution stepExecution) {
    ContextAndScope contextAndScope = executionContextStore.get(stepExecution);
    if (contextAndScope == null) {
      return null;
    }

    executionContextStore.put(stepExecution, null);
    contextAndScope.closeScope();
    stepExecutionInstrumenter().end(contextAndScope.getContext(), stepExecution, null, null);
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
    return o instanceof TracingStepExecutionListener;
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
