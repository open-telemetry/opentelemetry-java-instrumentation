/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.batch.job;

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
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.job.builder.JobBuilderHelper;

public class JobBuilderHelperInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    // Java DSL Job config
    return named("org.springframework.batch.core.job.builder.JobBuilderHelper");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("enhance")
            .and(isProtected())
            .and(takesArguments(1))
            .and(takesArgument(0, named("org.springframework.batch.core.Job"))),
        this.getClass().getName() + "$EnhanceAdvice");
  }

  @SuppressWarnings("unused")
  public static class EnhanceAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(@Advice.This JobBuilderHelper<?> jobBuilder) {
      VirtualField<JobExecution, ContextAndScope> executionVirtualField =
          VirtualField.find(JobExecution.class, ContextAndScope.class);
      jobBuilder.listener(new TracingJobExecutionListener(executionVirtualField));
    }
  }
}
