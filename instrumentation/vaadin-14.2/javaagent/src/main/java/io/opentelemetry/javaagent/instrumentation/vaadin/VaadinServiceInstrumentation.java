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

    public static class AdviceScope {
      private final VaadinServiceRequest request;
      private final Context context;
      private final Scope scope;

      private AdviceScope(VaadinServiceRequest request, Context context, Scope scope) {
        this.request = request;
        this.context = context;
        this.scope = scope;
      }

      @Nullable
      public static AdviceScope start(Class<?> serviceClass, String methodName) {
        VaadinServiceRequest request = VaadinServiceRequest.create(serviceClass, methodName);
        Context context = helper().startVaadinServiceSpan(request);
        if (context == null) {
          return null;
        }
        return new AdviceScope(request, context, context.makeCurrent());
      }

      public void end(@Nullable Throwable throwable) {
        scope.close();

        helper().endVaadinServiceSpan(context, request, throwable);
      }
    }

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AdviceScope onEnter(
        @Advice.This VaadinService vaadinService, @Advice.Origin("#m") String methodName) {

      return AdviceScope.start(vaadinService.getClass(), methodName);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter @Nullable AdviceScope adviceScope) {
      if (adviceScope != null) {
        adviceScope.end(throwable);
      }
    }
  }
}
