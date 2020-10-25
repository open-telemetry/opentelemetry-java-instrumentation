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
import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.instrumentation.api.ContextStore;
import io.opentelemetry.javaagent.instrumentation.api.InstrumentationContext;
import io.opentelemetry.javaagent.tooling.Instrumenter;
import java.util.HashMap;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public class ContextInstrumentation extends AbstractInstrumentation {

  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    return named("application.io.opentelemetry.context.Context");
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>();
    transformers.put(
        isMethod().and(isPublic()).and(isStatic()).and(named("current")).and(takesArguments(0)),
        ContextInstrumentation.class.getName() + "$CurrentAdvice");
    transformers.put(
        isMethod().and(isPublic()).and(named("makeCurrent")).and(takesArguments(0)),
        ContextInstrumentation.class.getName() + "$MakeCurrentAdvice");
    return transformers;
  }

  public static class CurrentAdvice {

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(@Advice.Return Context applicationContext) {
      ContextStore<Context, io.opentelemetry.context.Context> contextStore =
          InstrumentationContext.get(Context.class, io.opentelemetry.context.Context.class);
      contextStore.put(applicationContext, io.opentelemetry.context.Context.current());
    }
  }

  public static class MakeCurrentAdvice {

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.This Context applicationContext,
        @Advice.Return(readOnly = false) Scope applicationScope) {
      ContextStore<Context, io.opentelemetry.context.Context> contextStore =
          InstrumentationContext.get(Context.class, io.opentelemetry.context.Context.class);
      io.opentelemetry.context.Scope agentScope =
          contextStore.get(applicationContext).makeCurrent();
      applicationScope = agentScope::close;
    }
  }
}
