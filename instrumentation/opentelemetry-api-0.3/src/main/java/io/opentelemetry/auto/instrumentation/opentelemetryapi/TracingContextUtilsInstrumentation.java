/*
 * Copyright 2020, OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.opentelemetry.auto.instrumentation.opentelemetryapi;

import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import io.opentelemetry.auto.bootstrap.ContextStore;
import io.opentelemetry.auto.bootstrap.InstrumentationContext;
import io.opentelemetry.auto.instrumentation.opentelemetryapi.trace.TracingContextUtils;
import io.opentelemetry.auto.tooling.Instrumenter;
import java.util.HashMap;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import unshaded.io.grpc.Context;
import unshaded.io.opentelemetry.context.Scope;
import unshaded.io.opentelemetry.trace.Span;

@AutoService(Instrumenter.class)
public class TracingContextUtilsInstrumentation extends AbstractInstrumentation {

  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    return named("unshaded.io.opentelemetry.trace.TracingContextUtils");
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    final Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>();
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
        @Advice.Argument(0) final Span span,
        @Advice.Argument(1) final Context context,
        @Advice.Return(readOnly = false) Context updatedContext) {

      final ContextStore<Context, io.grpc.Context> contextStore =
          InstrumentationContext.get(Context.class, io.grpc.Context.class);
      updatedContext = TracingContextUtils.withSpan(span, context, contextStore);
    }
  }

  public static class GetCurrentSpanAdvice {

    @Advice.OnMethodEnter(skipOn = Advice.OnDefaultValue.class)
    public static Object onEnter() {
      return null;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(@Advice.Return(readOnly = false) Span span) {
      span = TracingContextUtils.getCurrentSpan();
    }
  }

  public static class GetSpanAdvice {

    @Advice.OnMethodEnter(skipOn = Advice.OnDefaultValue.class)
    public static Object onEnter() {
      return null;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Argument(0) final Context context, @Advice.Return(readOnly = false) Span span) {

      final ContextStore<Context, io.grpc.Context> contextStore =
          InstrumentationContext.get(Context.class, io.grpc.Context.class);
      span = TracingContextUtils.getSpan(context, contextStore);
    }
  }

  public static class GetSpanWithoutDefaultAdvice {

    @Advice.OnMethodEnter(skipOn = Advice.OnDefaultValue.class)
    public static Object onEnter() {
      return null;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Argument(0) final Context context, @Advice.Return(readOnly = false) Span span) {

      final ContextStore<Context, io.grpc.Context> contextStore =
          InstrumentationContext.get(Context.class, io.grpc.Context.class);
      span = TracingContextUtils.getSpanWithoutDefault(context, contextStore);
    }
  }

  public static class CurrentContextWithAdvice {

    @Advice.OnMethodEnter(skipOn = Advice.OnDefaultValue.class)
    public static Object onEnter() {
      return null;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Argument(0) final Span span, @Advice.Return(readOnly = false) Scope scope) {
      scope = TracingContextUtils.currentContextWith(span);
    }
  }
}
