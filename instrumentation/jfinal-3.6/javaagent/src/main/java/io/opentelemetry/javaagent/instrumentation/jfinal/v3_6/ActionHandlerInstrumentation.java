/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jfinal.v3_6;

import static io.opentelemetry.javaagent.instrumentation.jfinal.v3_6.JFinalSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class ActionHandlerInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.jfinal.core.ActionHandler");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("handle")
            .and(
                takesArguments(4)
                    .and(takesArgument(0, String.class))
                    .and(takesArgument(1, named("javax.servlet.http.HttpServletRequest")))
                    .and(takesArgument(2, named("javax.servlet.http.HttpServletResponse")))
                    .and(takesArgument(3, boolean[].class))),
        this.getClass().getName() + "$HandleAdvice");
  }

  @SuppressWarnings("unused")
  private static class HandleAdvice {

    public static class AdviceScope {
      private final Context context;
      private final Scope scope;

      public AdviceScope(Context context, Scope scope) {
        this.context = context;
        this.scope = scope;
      }

      @Nullable
      public static AdviceScope start() {
        Context parentContext = Context.current();
        if (!instrumenter().shouldStart(parentContext, null)) {
          return null;
        }

        Context context = instrumenter().start(parentContext, null);
        return new AdviceScope(context, context.makeCurrent());
      }

      public void end(@Nullable Throwable throwable) {
        scope.close();
        instrumenter().end(context, null, null, throwable);
      }
    }

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AdviceScope onEnter() {
      return AdviceScope.start();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopTraceOnResponse(
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter @Nullable AdviceScope actionScope) {
      if (actionScope != null) {
        actionScope.end(throwable);
      }
    }
  }
}
