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

public class JsrChunkProcessorInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    return named("org.springframework.batch.core.jsr.step.item.JsrChunkProcessor");
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>();
    transformers.put(
        isProtected().and(named("doProvide")).and(takesArguments(2)),
        this.getClass().getName() + "$ProvideAdvice");
    transformers.put(
        isProtected().and(named("doTransform")).and(takesArguments(1)),
        this.getClass().getName() + "$TransformAdvice");
    transformers.put(
        isProtected().and(named("doPersist")).and(takesArguments(2)),
        this.getClass().getName() + "$PersistAdvice");
    return transformers;
  }

  public static class ProvideAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Local("otelContext") Context context, @Advice.Local("otelScope") Scope scope) {
      if (!shouldTraceItems()) {
        return;
      }
      context = tracer().startReadSpan();
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

  public static class TransformAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Local("otelContext") Context context, @Advice.Local("otelScope") Scope scope) {
      if (!shouldTraceItems()) {
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

  public static class PersistAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Local("otelContext") Context context, @Advice.Local("otelScope") Scope scope) {
      if (!shouldTraceItems()) {
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
