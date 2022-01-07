/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.batch.step;

import static io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.spring.batch.step.StepSingletons.stepInstrumenter;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.field.VirtualField;
import io.opentelemetry.javaagent.instrumentation.spring.batch.ContextAndScope;
import javax.annotation.Nullable;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.core.Ordered;

public final class TracingStepExecutionListener implements StepExecutionListener, Ordered {
  private final VirtualField<StepExecution, ContextAndScope> executionVirtualField;

  public TracingStepExecutionListener(
      VirtualField<StepExecution, ContextAndScope> executionVirtualField) {
    this.executionVirtualField = executionVirtualField;
  }

  @Override
  public void beforeStep(StepExecution stepExecution) {
    Context parentContext = currentContext();
    if (!stepInstrumenter().shouldStart(parentContext, stepExecution)) {
      return;
    }

    Context context = stepInstrumenter().start(parentContext, stepExecution);
    // beforeStep & afterStep always execute on the same thread
    Scope scope = context.makeCurrent();
    executionVirtualField.set(stepExecution, new ContextAndScope(context, scope));
  }

  @Override
  public ExitStatus afterStep(StepExecution stepExecution) {
    ContextAndScope contextAndScope = executionVirtualField.get(stepExecution);
    if (contextAndScope == null) {
      return null;
    }

    executionVirtualField.set(stepExecution, null);
    contextAndScope.closeScope();
    stepInstrumenter().end(contextAndScope.getContext(), stepExecution, null, null);
    return null;
  }

  @Override
  public int getOrder() {
    return HIGHEST_PRECEDENCE;
  }

  // equals() and hashCode() methods guarantee that only one instance of
  // TracingStepExecutionListener will be present in an ordered set of listeners

  @Override
  public boolean equals(@Nullable Object o) {
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
