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

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.This VaadinService vaadinService,
        @Advice.Origin("#m") String methodName,
        @Advice.Local("otelRequest") VaadinServiceRequest request,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {

      request = VaadinServiceRequest.create(vaadinService.getClass(), methodName);
      context = helper().startVaadinServiceSpan(request);
      if (context != null) {
        scope = context.makeCurrent();
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelRequest") VaadinServiceRequest request,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      if (scope == null) {
        return;
      }
      scope.close();

      helper().endVaadinServiceSpan(context, request, throwable);
    }
  }
}
