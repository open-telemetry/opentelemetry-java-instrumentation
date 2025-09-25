/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vaadin;

import static io.opentelemetry.javaagent.instrumentation.vaadin.VaadinSingletons.helper;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.vaadin.flow.server.VaadinService;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

// add span around vaadin request processing code
public class VaadinServiceInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.vaadin.flow.server.VaadinService");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("handleRequest")
            .and(takesArgument(0, named("com.vaadin.flow.server.VaadinRequest")))
            .and(takesArgument(1, named("com.vaadin.flow.server.VaadinResponse"))),
        VaadinServiceInstrumentation.class.getName() + "$HandleRequestAdvice");
  }

  @SuppressWarnings("unused")
  public static class HandleRequestAdvice {

    public static class HandleRequestAdviceScope {
      private final VaadinServiceRequest request;
      private final Context context;
      private final Scope scope;

      private HandleRequestAdviceScope(VaadinServiceRequest request, Context context, Scope scope) {
        this.request = request;
        this.context = context;
        this.scope = scope;
      }

      @Nullable
      public static HandleRequestAdviceScope start(Class<?> serviceClass, String methodName) {
        VaadinServiceRequest request = VaadinServiceRequest.create(serviceClass, methodName);
        Context context = helper().startVaadinServiceSpan(request);
        if (context == null) {
          return null;
        }
        Scope scope = context.makeCurrent();
        return new HandleRequestAdviceScope(request, context, scope);
      }

      public void end(@Nullable Throwable throwable) {
        scope.close();

        helper().endVaadinServiceSpan(context, request, throwable);
      }
    }

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static HandleRequestAdviceScope onEnter(
        @Advice.This VaadinService vaadinService, @Advice.Origin("#m") String methodName) {

      return HandleRequestAdviceScope.start(vaadinService.getClass(), methodName);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter @Nullable HandleRequestAdviceScope adviceScope) {
      if (adviceScope == null) {
        return;
      }
      adviceScope.end(throwable);
    }
  }
}
