/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vaadin;

import static io.opentelemetry.javaagent.instrumentation.vaadin.VaadinSingletons.clientCallableInstrumenter;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

// add spans around calls to methods with @ClientCallable annotation
public class ClientCallableRpcInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.vaadin.flow.server.communication.rpc.PublishedServerEventHandlerRpcHandler");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("invokeMethod")
            .and(takesArgument(0, named("com.vaadin.flow.component.Component")))
            .and(takesArgument(1, named(Class.class.getName())))
            .and(takesArgument(2, named(String.class.getName())))
            .and(takesArgument(3, named("elemental.json.JsonArray")))
            .and(takesArgument(4, named(int.class.getName()))),
        this.getClass().getName() + "$InvokeMethodAdvice");
  }

  @SuppressWarnings("unused")
  public static class InvokeMethodAdvice {

    public static class InvokeMethodAdviceScope {
      private final VaadinClientCallableRequest request;
      private final Context context;
      private final Scope scope;

      private InvokeMethodAdviceScope(
          VaadinClientCallableRequest request, Context context, Scope scope) {
        this.request = request;
        this.context = context;
        this.scope = scope;
      }

      @Nullable
      public static InvokeMethodAdviceScope start(Class<?> componentClass, String methodName) {
        Context parentContext = Context.current();
        VaadinClientCallableRequest request =
            VaadinClientCallableRequest.create(componentClass, methodName);
        if (!clientCallableInstrumenter().shouldStart(parentContext, request)) {
          return null;
        }

        Context context = clientCallableInstrumenter().start(parentContext, request);
        Scope scope = context.makeCurrent();
        return new InvokeMethodAdviceScope(request, context, scope);
      }

      public void end(@Nullable Throwable throwable) {
        scope.close();

        clientCallableInstrumenter().end(context, request, null, throwable);
      }
    }

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static InvokeMethodAdviceScope onEnter(
        @Advice.Argument(1) Class<?> componentClass, @Advice.Argument(2) String methodName) {
      return InvokeMethodAdviceScope.start(componentClass, methodName);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter @Nullable InvokeMethodAdviceScope adviceScope) {
      if (adviceScope == null) {
        return;
      }
      adviceScope.end(throwable);
    }
  }
}
