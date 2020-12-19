/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.batch.chunk;

import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
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
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.builder.AbstractTaskletStepBuilder;

public class StepBuilderInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    // Spring Batch Java DSL and XML config
    return named("org.springframework.batch.core.step.builder.AbstractTaskletStepBuilder")
        // JSR-352 XML config
        .or(named("org.springframework.batch.core.jsr.step.builder.JsrSimpleStepBuilder"))
        .or(named("org.springframework.batch.core.jsr.step.builder.JsrBatchletStepBuilder"));
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        named("build").and(isPublic()).and(takesArguments(0)),
        this.getClass().getName() + "$BuildAdvice");
  }

  public static class BuildAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(@Advice.This AbstractTaskletStepBuilder<?> stepBuilder) {
      ContextStore<ChunkContext, ContextAndScope> chunkExecutionContextStore =
          InstrumentationContext.get(ChunkContext.class, ContextAndScope.class);
      stepBuilder.listener(new TracingChunkExecutionListener(chunkExecutionContextStore));
    }
  }
}
