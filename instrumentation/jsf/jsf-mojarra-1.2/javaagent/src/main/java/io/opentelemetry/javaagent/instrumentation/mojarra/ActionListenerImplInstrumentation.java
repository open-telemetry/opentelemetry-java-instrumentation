/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mojarra;

import static io.opentelemetry.javaagent.instrumentation.mojarra.MojarraSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.jsf.javax.JsfRequest;
import javax.annotation.Nullable;
import javax.faces.event.ActionEvent;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class ActionListenerImplInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.sun.faces.application.ActionListenerImpl");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("processAction"),
        ActionListenerImplInstrumentation.class.getName() + "$ProcessActionAdvice");
  }

  @SuppressWarnings("unused")
  public static class ProcessActionAdvice {

    public static class AdviceScope {
      private final JsfRequest request;
      private final Context context;
      private final Scope scope;

      private AdviceScope(JsfRequest request, Context context, Scope scope) {
        this.request = request;
        this.context = context;
        this.scope = scope;
      }

      @Nullable
      public static AdviceScope start(@Advice.Argument(0) ActionEvent event) {
        Context parentContext = Context.current();
        JsfRequest request = new JsfRequest(event);
        if (!request.shouldStartSpan() || !instrumenter().shouldStart(parentContext, request)) {
          return null;
        }
        Context context = instrumenter().start(parentContext, request);
        return new AdviceScope(request, context, context.makeCurrent());
      }

      public void end(@Nullable Throwable throwable) {
        scope.close();
        instrumenter().end(context, request, null, throwable);
      }
    }

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AdviceScope onEnter(@Advice.Argument(0) ActionEvent event) {
      return AdviceScope.start(event);
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
