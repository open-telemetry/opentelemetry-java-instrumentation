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

import application.io.grpc.Context;
import application.io.opentelemetry.context.Scope;
import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.instrumentation.api.ContextStore;
import io.opentelemetry.javaagent.instrumentation.api.InstrumentationContext;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.context.ContextUtils;
import io.opentelemetry.javaagent.tooling.Instrumenter;
import java.util.HashMap;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public class ContextUtilsInstrumentation extends AbstractInstrumentation {

  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    return named("application.io.opentelemetry.context.ContextUtils");
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>();
    transformers.put(
        isMethod()
            .and(isPublic())
            .and(isStatic())
            .and(named("withScopedContext"))
            .and(takesArguments(1)),
        ContextUtilsInstrumentation.class.getName() + "$WithScopedContextAdvice");
    return transformers;
  }

  public static class WithScopedContextAdvice {

    @Advice.OnMethodEnter(skipOn = Advice.OnDefaultValue.class)
    public static Object onEnter() {
      return null;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Argument(0) Context applicationContext,
        @Advice.Return(readOnly = false) Scope applicationScope) {

      ContextStore<Context, io.grpc.Context> contextStore =
          InstrumentationContext.get(Context.class, io.grpc.Context.class);
      applicationScope = ContextUtils.withScopedContext(applicationContext, contextStore);
    }
  }
}
