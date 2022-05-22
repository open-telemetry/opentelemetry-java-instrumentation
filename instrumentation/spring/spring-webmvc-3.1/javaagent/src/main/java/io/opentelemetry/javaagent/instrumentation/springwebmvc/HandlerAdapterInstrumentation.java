/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.springwebmvc;

import static io.opentelemetry.instrumentation.api.instrumenter.http.HttpRouteSource.CONTROLLER;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.instrumentation.springwebmvc.IsGrailsHandler.isGrailsHandler;
import static io.opentelemetry.javaagent.instrumentation.springwebmvc.SpringWebMvcSingletons.handlerInstrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpRouteHolder;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.servlet.http.HttpServletRequest;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class HandlerAdapterInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("org.springframework.web.servlet.HandlerAdapter");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("org.springframework.web.servlet.HandlerAdapter"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(isPublic())
            .and(nameStartsWith("handle"))
            .and(takesArgument(0, named("javax.servlet.http.HttpServletRequest")))
            .and(takesArguments(3)),
        HandlerAdapterInstrumentation.class.getName() + "$ControllerAdvice");
  }

  @SuppressWarnings("unused")
  public static class ControllerAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void nameResourceAndStartSpan(
        @Advice.Argument(0) HttpServletRequest request,
        @Advice.Argument(2) Object handler,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      // TODO (trask) should there be a way to customize Instrumenter.shouldStart()?
      if (isGrailsHandler(handler)) {
        // skip creating handler span for grails, grails instrumentation will take care of it
        return;
      }

      Context parentContext = Java8BytecodeBridge.currentContext();

      // don't start a new top-level span
      if (!Java8BytecodeBridge.spanFromContext(parentContext).getSpanContext().isValid()) {
        return;
      }

      // Name the parent span based on the matching pattern
      HttpRouteHolder.updateHttpRoute(
          parentContext, CONTROLLER, SpringWebMvcServerSpanNaming.SERVER_SPAN_NAME, request);

      if (!handlerInstrumenter().shouldStart(parentContext, handler)) {
        return;
      }

      // Now create a span for handler/controller execution.
      context = handlerInstrumenter().start(parentContext, handler);
      scope = context.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Argument(2) Object handler,
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      if (scope == null) {
        return;
      }
      scope.close();
      handlerInstrumenter().end(context, handler, null, throwable);
    }
  }
}
