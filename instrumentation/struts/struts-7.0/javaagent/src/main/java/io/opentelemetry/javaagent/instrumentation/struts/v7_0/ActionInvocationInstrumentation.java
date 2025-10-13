/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.struts.v7_0;

import static io.opentelemetry.instrumentation.api.semconv.http.HttpServerRouteSource.CONTROLLER;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.instrumentation.struts.v7_0.StrutsSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRoute;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.struts2.ActionInvocation;

public class ActionInvocationInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("org.apache.struts2.ActionInvocation");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("org.apache.struts2.ActionInvocation"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod().and(isPublic()).and(named("invokeActionOnly")),
        this.getClass().getName() + "$InvokeActionOnlyAdvice");
  }

  @SuppressWarnings("unused")
  public static class InvokeActionOnlyAdvice {

    public static class AdviceScope {
      private final Context context;
      private final Scope scope;

      public AdviceScope(Context context, Scope scope) {
        this.context = context;
        this.scope = scope;
      }

      @Nullable
      public static AdviceScope start(ActionInvocation actionInvocation) {
        Context parentContext = Context.current();
        HttpServerRoute.update(
            parentContext,
            CONTROLLER,
            StrutsServerSpanNaming.SERVER_SPAN_NAME,
            actionInvocation.getProxy());

        if (!instrumenter().shouldStart(parentContext, actionInvocation)) {
          return null;
        }
        Context context = instrumenter().start(parentContext, actionInvocation);
        return new AdviceScope(context, context.makeCurrent());
      }

      public void end(ActionInvocation actionInvocation, @Nullable Throwable throwable) {
        scope.close();
        instrumenter().end(context, actionInvocation, null, throwable);
      }
    }

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AdviceScope onEnter(@Advice.This ActionInvocation actionInvocation) {
      return AdviceScope.start(actionInvocation);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.This ActionInvocation actionInvocation,
        @Advice.Enter @Nullable AdviceScope adviceScope) {
      if (adviceScope != null) {
        adviceScope.end(actionInvocation, throwable);
      }
    }
  }
}
