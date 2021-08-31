/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.struts2;

import static io.opentelemetry.instrumentation.api.servlet.ServerSpanNaming.Source.CONTROLLER;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.instrumentation.struts2.StrutsSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.opensymphony.xwork2.ActionInvocation;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.servlet.ServerSpanNaming;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class ActionInvocationInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("com.opensymphony.xwork2.ActionInvocation");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("com.opensymphony.xwork2.ActionInvocation"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod().and(isPublic()).and(named("invokeActionOnly")),
        this.getClass().getName() + "$InvokeActionOnlyAdvice");
  }

  @SuppressWarnings("unused")
  public static class InvokeActionOnlyAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.This ActionInvocation actionInvocation,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      Context parentContext = Java8BytecodeBridge.currentContext();

      ServerSpanNaming.updateServerSpanName(
          parentContext,
          CONTROLLER,
          StrutsServerSpanNaming.SERVER_SPAN_NAME,
          actionInvocation.getProxy());

      if (!instrumenter().shouldStart(parentContext, actionInvocation)) {
        return;
      }

      context = instrumenter().start(parentContext, actionInvocation);
      scope = context.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Thrown Throwable throwable,
        @Advice.This ActionInvocation actionInvocation,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      if (scope == null) {
        return;
      }
      scope.close();

      instrumenter().end(context, actionInvocation, null, throwable);
    }
  }
}
