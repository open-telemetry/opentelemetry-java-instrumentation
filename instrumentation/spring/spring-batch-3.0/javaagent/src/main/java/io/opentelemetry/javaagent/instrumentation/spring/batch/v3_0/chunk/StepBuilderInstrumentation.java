/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.chunk;

import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.springframework.batch.core.step.builder.AbstractTaskletStepBuilder;

public class StepBuilderInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    // Spring Batch Java DSL and XML config
    return namedOneOf(
        "org.springframework.batch.core.step.builder.AbstractTaskletStepBuilder",
        // JSR-352 XML config
        "org.springframework.batch.core.jsr.step.builder.JsrSimpleStepBuilder",
        "org.springframework.batch.core.jsr.step.builder.JsrBatchletStepBuilder");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("build").and(isPublic()).and(takesArguments(0)),
        this.getClass().getName() + "$BuildAdvice");
  }

  @SuppressWarnings("unused")
  public static class BuildAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(@Advice.This AbstractTaskletStepBuilder<?> stepBuilder) {
      stepBuilder.listener(new TracingChunkExecutionListener(stepBuilder.getClass()));
    }
  }
}
