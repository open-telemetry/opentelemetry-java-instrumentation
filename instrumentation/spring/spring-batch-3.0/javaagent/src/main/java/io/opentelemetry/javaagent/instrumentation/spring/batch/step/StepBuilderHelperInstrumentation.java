/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.batch.step;

import static net.bytebuddy.matcher.ElementMatchers.isProtected;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.spring.batch.ContextAndScope;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.step.builder.StepBuilderHelper;

public class StepBuilderHelperInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.springframework.batch.core.step.builder.StepBuilderHelper");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("enhance")
            .and(isProtected())
            .and(takesArguments(1))
            .and(takesArgument(0, named("org.springframework.batch.core.Step"))),
        this.getClass().getName() + "$EnhanceAdvice");
  }

  @SuppressWarnings("unused")
  public static class EnhanceAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(@Advice.This StepBuilderHelper<?> stepBuilder) {
      VirtualField<StepExecution, ContextAndScope> executionVirtualField =
          VirtualField.find(StepExecution.class, ContextAndScope.class);
      stepBuilder.listener(new TracingStepExecutionListener(executionVirtualField));
    }
  }
}
