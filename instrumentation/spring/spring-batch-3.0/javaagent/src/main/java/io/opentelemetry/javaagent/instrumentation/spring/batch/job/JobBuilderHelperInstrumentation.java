/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.batch.job;

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
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.job.builder.JobBuilderHelper;

public class JobBuilderHelperInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    // Java DSL Job config
    return named("org.springframework.batch.core.job.builder.JobBuilderHelper");
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        named("enhance")
            .and(isProtected())
            .and(takesArguments(1))
            .and(takesArgument(0, named("org.springframework.batch.core.Job"))),
        this.getClass().getName() + "$EnhanceAdvice");
  }

  public static class EnhanceAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(@Advice.This JobBuilderHelper<?> jobBuilder) {
      ContextStore<JobExecution, ContextAndScope> executionContextStore =
          InstrumentationContext.get(JobExecution.class, ContextAndScope.class);
      jobBuilder.listener(new TracingJobExecutionListener(executionContextStore));
    }
  }
}
