/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rmi.client;

import static io.opentelemetry.javaagent.instrumentation.rmi.client.RmiClientSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.lang.reflect.Method;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class UnicastRefInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("sun.rmi.server.UnicastRef");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("invoke"))
            .and(takesArgument(0, named("java.rmi.Remote")))
            .and(takesArgument(1, Method.class)),
        this.getClass().getName() + "$InvokeAdvice");
  }

  @SuppressWarnings("unused")
  public static class InvokeAdvice {

    public static class AdviceScope {
      public Context context;
      public Scope scope;

      private AdviceScope(Context context, Scope scope) {
        this.context = context;
        this.scope = scope;
      }

      @Nullable
      public static AdviceScope start(Method method) {
        Context parentContext = Context.current();
        if (!instrumenter().shouldStart(parentContext, method)) {
          return null;
        }
        Context context = instrumenter().start(parentContext, method);
        return new AdviceScope(context, context.makeCurrent());
      }

      public void end(Method method, @Nullable Throwable throwable) {
        scope.close();
        instrumenter().end(context, method, null, throwable);
      }
    }

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AdviceScope onEnter(@Advice.Argument(value = 1) Method method) {
      return AdviceScope.start(method);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Argument(value = 1) Method method,
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter @Nullable AdviceScope adviceScope) {
      if (adviceScope != null) {
        adviceScope.end(method, throwable);
      }
    }
  }
}
