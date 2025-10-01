/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Context;
//import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class HandlerInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("io.vertx.core.Handler");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
//    System.out.println("HANDLER-INSTRUMENTATION: Checking type matcher for Handler interface");
    // Target the Handler interface directly - this won't work for lambdas but let's try
    return implementsInterface(named("io.vertx.core.Handler"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
//    System.out.println("HANDLER-INSTRUMENTATION: Applying transformations");
    // Only intercept the handle method - constructors won't work for lambdas
    transformer.applyAdviceToMethod(
        named("handle").and(takesArgument(0, Object.class)),
        HandlerInstrumentation.class.getName() + "$HandleAdvice");
  }


  @SuppressWarnings("unused")
  public static class HandleAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(@Advice.This Object handler) {
//      System.out.println("HANDLER-EXECUTE: Handler executing: " + handler + " (class: " + (handler != null ? handler.getClass().getName() : "null") + ")");
      
      // Just use the current context - we can't store context in lambdas
      Context currentContext = Context.current();
//      System.out.println("HANDLER-EXECUTE: Current context: " + currentContext);
      
      // Only make current if we have a valid context
      if (currentContext != null && currentContext != Context.root()) {
        // Debug logging
        String threadName = Thread.currentThread().getName();
        String handlerClass = handler != null ? handler.getClass().getSimpleName() : "null";
        io.opentelemetry.api.trace.Span span = io.opentelemetry.api.trace.Span.fromContext(currentContext);
//        System.out.println("HANDLER EXECUTE [" + threadName + "]: Executing handler " + handlerClass +
//          " with current context span: " + (span != null && span.getSpanContext().isValid() ? span.getSpanContext().getSpanId() : "null") +
//          ", traceId: " + (span != null && span.getSpanContext().isValid() ? span.getSpanContext().getTraceId() : "null"));

//        return currentContext.makeCurrent();
      }
      
//      return null;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
//        @Advice.Enter Scope scope
    ) {
//      if (scope != null) {
//        scope.close();
//      }
    }
  }
}
