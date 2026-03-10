/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jfinal.v3_2;

import static io.opentelemetry.javaagent.instrumentation.jfinal.v3_2.JFinalSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesNoArguments;

import com.jfinal.aop.Invocation;
import com.jfinal.core.Action;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.incubator.semconv.util.ClassAndMethod;
import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class InvocationInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.jfinal.aop.Invocation");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("invoke").and(takesNoArguments()), this.getClass().getName() + "$InvokeAdvice");
  }

  @SuppressWarnings("unused")
  public static class InvokeAdvice {

    public static class AdviceScope {
      private final CallDepth callDepth;
      private final ClassAndMethod request;
      private final Context context;
      private final Scope scope;

      public AdviceScope(
          CallDepth callDepth, ClassAndMethod request, Context context, Scope scope) {
        this.callDepth = callDepth;
        this.request = request;
        this.context = context;
        this.scope = scope;
      }

      public static AdviceScope start(CallDepth callDepth, Action action) {
        if (callDepth.getAndIncrement() > 0 || action == null) {
          return new AdviceScope(callDepth, null, null, null);
        }

        Context parentContext = Context.current();
        ClassAndMethod request =
            ClassAndMethod.create(action.getControllerClass(), action.getMethodName());
        if (!instrumenter().shouldStart(parentContext, request)) {
          return new AdviceScope(callDepth, null, null, null);
        }

        Context context = instrumenter().start(parentContext, request);
        return new AdviceScope(callDepth, request, context, context.makeCurrent());
      }

      public void end(@Nullable Throwable throwable) {
        if (callDepth.decrementAndGet() > 0 || scope == null) {
          return;
        }
        scope.close();
        instrumenter().end(context, request, null, throwable);
      }
    }

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AdviceScope onEnter(@Advice.FieldValue("action") Action action) {
      CallDepth callDepth = CallDepth.forClass(Invocation.class);
      return AdviceScope.start(callDepth, action);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopTraceOnResponse(
        @Advice.Thrown @Nullable Throwable throwable, @Advice.Enter AdviceScope actionScope) {
      actionScope.end(throwable);
    }
  }
}
