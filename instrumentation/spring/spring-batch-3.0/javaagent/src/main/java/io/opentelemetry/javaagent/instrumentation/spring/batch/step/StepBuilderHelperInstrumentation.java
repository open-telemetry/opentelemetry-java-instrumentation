/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.batch.step;

import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isProtected;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.instrumentation.api.ContextStore;
import io.opentelemetry.javaagent.instrumentation.api.InstrumentationContext;
import io.opentelemetry.javaagent.instrumentation.spring.batch.ContextAndScope;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.step.builder.StepBuilderHelper;

public class StepBuilderHelperInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    return named("org.springframework.batch.core.step.builder.StepBuilderHelper");
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        named("enhance")
            .and(isProtected())
            .and(takesArguments(1))
            .and(takesArgument(0, named("org.springframework.batch.core.Step"))),
        this.getClass().getName() + "$EnhanceAdvice");
  }

  public static class EnhanceAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(@Advice.This StepBuilderHelper<?> stepBuilder) {
      ContextStore<StepExecution, ContextAndScope> executionContextStore =
          InstrumentationContext.get(StepExecution.class, ContextAndScope.class);
      stepBuilder.listener(new TracingStepExecutionListener(executionContextStore));
    }
  }
}
