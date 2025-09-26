/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vaadin;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.instrumentation.vaadin.VaadinSingletons.helper;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.vaadin.flow.server.RequestHandler;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

// add spans around vaadin request handlers
public class RequestHandlerInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("com.vaadin.flow.server.RequestHandler");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("com.vaadin.flow.server.RequestHandler"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("handleRequest")
            .and(takesArgument(0, named("com.vaadin.flow.server.VaadinSession")))
            .and(takesArgument(1, named("com.vaadin.flow.server.VaadinRequest")))
            .and(takesArgument(2, named("com.vaadin.flow.server.VaadinResponse"))),
        this.getClass().getName() + "$HandleRequestAdvice");
  }

  @SuppressWarnings("unused")
  public static class HandleRequestAdvice {
    public static class AdviceScope {
      private final VaadinHandlerRequest request;
      private final Context context;
      private final Scope scope;

      private AdviceScope(VaadinHandlerRequest request, Context context, Scope scope) {
        this.request = request;
        this.context = context;
        this.scope = scope;
      }

      @Nullable
      public static AdviceScope start(Class<?> handlerClass, String methodName) {
        VaadinHandlerRequest request = VaadinHandlerRequest.create(handlerClass, methodName);
        Context context = helper().startRequestHandlerSpan(request);
        if (context == null) {
          return null;
        }
        Scope scope = context.makeCurrent();
        return new AdviceScope(request, context, scope);
      }

      public void end(@Nullable Throwable throwable, boolean handled) {
        scope.close();

        helper().endRequestHandlerSpan(context, request, throwable, handled);
      }
    }

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AdviceScope onEnter(
        @Advice.This RequestHandler requestHandler, @Advice.Origin("#m") String methodName) {

      return AdviceScope.start(requestHandler.getClass(), methodName);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.Thrown Throwable throwable,
        @Advice.Return boolean handled,
        @Advice.Enter @Nullable AdviceScope adviceScope) {
      if (adviceScope != null) {
        adviceScope.end(throwable, handled);
      }
    }
  }
}
