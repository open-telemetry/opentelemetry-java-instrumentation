/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.batch.item;

import static io.opentelemetry.javaagent.instrumentation.spring.batch.SpringBatchInstrumentationConfig.shouldTraceItems;
import static io.opentelemetry.javaagent.instrumentation.spring.batch.item.ItemTracer.tracer;
import static net.bytebuddy.matcher.ElementMatchers.isProtected;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.HashMap;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;

public class SimpleChunkProcessorInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    return named("org.springframework.batch.core.step.item.SimpleChunkProcessor");
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>();
    transformers.put(
        isProtected().and(named("doProcess")).and(takesArguments(1)),
        this.getClass().getName() + "$ProcessAdvice");
    transformers.put(
        isProtected().and(named("doWrite")).and(takesArguments(1)),
        this.getClass().getName() + "$WriteAdvice");
    return transformers;
  }

  public static class ProcessAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.FieldValue("itemProcessor") ItemProcessor<?, ?> itemProcessor,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      if (!shouldTraceItems()) {
        return;
      }
      if (itemProcessor == null) {
        return;
      }
      context = tracer().startProcessSpan();
      if (context != null) {
        scope = context.makeCurrent();
      }
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void onExit(
        @Advice.Thrown Throwable thrown,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      if (scope != null) {
        scope.close();
        if (thrown == null) {
          tracer().end(context);
        } else {
          tracer().endExceptionally(context, thrown);
        }
      }
    }
  }

  public static class WriteAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.FieldValue("itemWriter") ItemWriter<?> itemWriter,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      if (!shouldTraceItems()) {
        return;
      }
      if (itemWriter == null) {
        return;
      }
      context = tracer().startWriteSpan();
      if (context != null) {
        scope = context.makeCurrent();
      }
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void onExit(
        @Advice.Thrown Throwable thrown,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      if (scope != null) {
        scope.close();
        if (thrown == null) {
          tracer().end(context);
        } else {
          tracer().endExceptionally(context, thrown);
        }
      }
    }
  }
}
