/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi;

import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import application.io.opentelemetry.context.Context;
import application.io.opentelemetry.context.Scope;
import application.io.opentelemetry.trace.Span;
import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.instrumentation.api.ContextStore;
import io.opentelemetry.javaagent.instrumentation.api.InstrumentationContext;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace.TracingContextUtils;
import io.opentelemetry.javaagent.tooling.Instrumenter;
import java.util.HashMap;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public class TracingContextUtilsInstrumentation extends AbstractInstrumentation {

  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    return named("application.io.opentelemetry.trace.TracingContextUtils");
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>();
    transformers.put(
        isMethod().and(isPublic()).and(isStatic()).and(named("withSpan")).and(takesArguments(2)),
        TracingContextUtilsInstrumentation.class.getName() + "$WithSpanAdvice");
    transformers.put(
        isMethod()
            .and(isPublic())
            .and(isStatic())
            .and(named("getCurrentSpan"))
            .and(takesArguments(0)),
        TracingContextUtilsInstrumentation.class.getName() + "$GetCurrentSpanAdvice");
    transformers.put(
        isMethod().and(isPublic()).and(isStatic()).and(named("getSpan")).and(takesArguments(1)),
        TracingContextUtilsInstrumentation.class.getName() + "$GetSpanAdvice");
    transformers.put(
        isMethod()
            .and(isPublic())
            .and(isStatic())
            .and(named("getSpanWithoutDefault"))
            .and(takesArguments(1)),
        TracingContextUtilsInstrumentation.class.getName() + "$GetSpanWithoutDefaultAdvice");
    transformers.put(
        isMethod()
            .and(isPublic())
            .and(isStatic())
            .and(named("currentContextWith"))
            .and(takesArguments(1)),
        TracingContextUtilsInstrumentation.class.getName() + "$CurrentContextWithAdvice");
    return transformers;
  }

  public static class WithSpanAdvice {

    @Advice.OnMethodEnter(skipOn = Advice.OnDefaultValue.class)
    public static Object onEnter() {
      return null;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Argument(0) Span applicationSpan,
        @Advice.Argument(1) Context applicationContext,
        @Advice.Return(readOnly = false) Context applicationUpdatedContext) {

      ContextStore<Context, io.opentelemetry.context.Context> contextStore =
          InstrumentationContext.get(Context.class, io.opentelemetry.context.Context.class);
      applicationUpdatedContext =
          TracingContextUtils.withSpan(applicationSpan, applicationContext, contextStore);
    }
  }

  public static class GetCurrentSpanAdvice {

    @Advice.OnMethodEnter(skipOn = Advice.OnDefaultValue.class)
    public static Object onEnter() {
      return null;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(@Advice.Return(readOnly = false) Span applicationSpan) {
      applicationSpan = TracingContextUtils.getCurrentSpan();
    }
  }

  public static class GetSpanAdvice {

    @Advice.OnMethodEnter(skipOn = Advice.OnDefaultValue.class)
    public static Object onEnter() {
      return null;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Argument(0) Context context,
        @Advice.Return(readOnly = false) Span applicationSpan) {

      ContextStore<Context, io.opentelemetry.context.Context> contextStore =
          InstrumentationContext.get(Context.class, io.opentelemetry.context.Context.class);
      applicationSpan = TracingContextUtils.getSpan(context, contextStore);
    }
  }

  public static class GetSpanWithoutDefaultAdvice {

    @Advice.OnMethodEnter(skipOn = Advice.OnDefaultValue.class)
    public static Object onEnter() {
      return null;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Argument(0) Context context,
        @Advice.Return(readOnly = false) Span applicationSpan) {

      ContextStore<Context, io.opentelemetry.context.Context> contextStore =
          InstrumentationContext.get(Context.class, io.opentelemetry.context.Context.class);
      applicationSpan = TracingContextUtils.getSpanWithoutDefault(context, contextStore);
    }
  }

  public static class CurrentContextWithAdvice {

    @Advice.OnMethodEnter(skipOn = Advice.OnDefaultValue.class)
    public static Object onEnter() {
      return null;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Argument(0) Span applicationSpan,
        @Advice.Return(readOnly = false) Scope applicationScope) {
      applicationScope = TracingContextUtils.currentContextWith(applicationSpan);
    }
  }
}
