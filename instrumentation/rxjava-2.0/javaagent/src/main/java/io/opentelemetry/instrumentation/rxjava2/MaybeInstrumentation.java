/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rxjava2;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.instrumentation.api.InstrumentationContext;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import io.reactivex.Maybe;
import io.reactivex.MaybeObserver;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class MaybeInstrumentation extends InstrumentationModule {

  public MaybeInstrumentation() {
    super("rxjava");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return Collections.singletonList(new Instrumentation());
  }

  public static class Instrumentation implements TypeInstrumentation {

    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
      return named("io.reactivex.Maybe");
    }

    @Override
    public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
      Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>();
      transformers.put(isConstructor(), CaptureParentSpanAdvice.class.getName());
      transformers.put(
          isMethod()
              .and(named("subscribe"))
              .and(takesArguments(1))
              .and(takesArgument(0, named("io.reactivex.MaybeObserver"))),
          PropagateParentSpanAdvice.class.getName());
      return transformers;
    }
  }

  public static class CaptureParentSpanAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onConstruct(@Advice.This final Maybe<?> maybe) {
      Context parentSpan = Context.current();
      if (parentSpan != null) {
        InstrumentationContext.get(Maybe.class, Context.class).put(maybe, parentSpan);
      }
    }
  }

  public static class PropagateParentSpanAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Scope onSubscribe(
        @Advice.This final Maybe<?> maybe,
        @Advice.Argument(value = 0, readOnly = false) MaybeObserver<?> observer) {
      if (observer != null) {
        Context parentSpan = InstrumentationContext.get(Maybe.class, Context.class).get(maybe);
        if (parentSpan != null) {
          // wrap the observer so spans from its events treat the captured span as their parent
          observer = new TracingMaybeObserver<>(observer, parentSpan);
          // activate the span here in case additional observers are created during subscribe
          return parentSpan.makeCurrent();
        }
      }
      return null;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void closeScope(@Advice.Enter final Scope scope) {
      if (scope != null) {
        scope.close();
      }
    }
  }
}
