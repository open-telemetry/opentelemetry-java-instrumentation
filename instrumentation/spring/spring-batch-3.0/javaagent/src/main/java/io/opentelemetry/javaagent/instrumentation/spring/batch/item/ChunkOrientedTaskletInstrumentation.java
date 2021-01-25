/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.batch.item;

import static io.opentelemetry.javaagent.instrumentation.spring.batch.SpringBatchInstrumentationConfig.shouldTraceItems;
import static io.opentelemetry.javaagent.instrumentation.spring.batch.item.ItemTracer.tracer;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.springframework.batch.core.scope.context.ChunkContext;

public class ChunkOrientedTaskletInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    return named("org.springframework.batch.core.step.item.ChunkOrientedTasklet");
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isPublic()
            .and(named("execute"))
            .and(takesArguments(2))
            .and(takesArgument(0, named("org.springframework.batch.core.StepContribution")))
            .and(
                takesArgument(
                    1, named("org.springframework.batch.core.scope.context.ChunkContext"))),
        this.getClass().getName() + "$ExecuteAdvice");
  }

  public static class ExecuteAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(1) ChunkContext chunkContext, @Advice.Local("otelScope") Scope scope) {
      if (shouldTraceItems()) {
        Context context = tracer().startChunk(chunkContext);
        scope = context.makeCurrent();
      }
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void onExit(@Advice.Local("otelScope") Scope scope) {
      if (scope != null) {
        scope.close();
      }
    }
  }
}
