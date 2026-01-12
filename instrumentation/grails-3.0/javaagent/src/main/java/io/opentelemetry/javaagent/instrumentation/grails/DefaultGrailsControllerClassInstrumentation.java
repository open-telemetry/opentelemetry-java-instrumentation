/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.grails;

import static io.opentelemetry.javaagent.instrumentation.grails.GrailsSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
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

public class DefaultGrailsControllerClassInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.grails.core.DefaultGrailsControllerClass");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(isPublic())
            .and(named("invoke"))
            .and(takesArgument(0, named(Object.class.getName())))
            .and(takesArgument(1, named(String.class.getName())))
            .and(takesArguments(2)),
        DefaultGrailsControllerClassInstrumentation.class.getName() + "$ControllerAdvice");
  }

  @SuppressWarnings("unused")
  public static class ControllerAdvice {

    public static class AdviceScope {
      private final HandlerData handlerData;
      private final Context context;
      private final Scope scope;

      private AdviceScope(HandlerData handlerData, Context context, Scope scope) {
        this.handlerData = handlerData;
        this.context = context;
        this.scope = scope;
      }

      @Nullable
      public static AdviceScope start(
          Object controller, @Nullable String action, String defaultActionName) {
        Context parentContext = Context.current();
        HandlerData handlerData =
            new HandlerData(controller, action != null ? action : defaultActionName);
        if (!instrumenter().shouldStart(parentContext, handlerData)) {
          return null;
        }

        Context context = instrumenter().start(parentContext, handlerData);
        return new AdviceScope(handlerData, context, context.makeCurrent());
      }

      public void end(Throwable throwable) {
        scope.close();
        instrumenter().end(context, handlerData, null, throwable);
      }
    }

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AdviceScope startSpan(
        @Advice.Argument(0) Object controller,
        @Advice.Argument(1) @Nullable String action,
        @Advice.FieldValue("defaultActionName") String defaultActionName) {

      return AdviceScope.start(controller, action, defaultActionName);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter @Nullable AdviceScope adviceScope) {
      if (adviceScope != null) {
        adviceScope.end(throwable);
      }
    }
  }
}
