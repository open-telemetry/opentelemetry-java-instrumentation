/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.universal;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPrivate;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.isAbstract;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.vertx.core.Handler;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Universal instrumentation for any Vertx method that accepts a Handler parameter.
 *
 * <p>This instrumentation is dynamically configured via InstrumentationTarget tuples and wraps
 * Handler arguments with UniversalContextPreservingHandler to ensure context propagation.
 */
public final class UniversalHandlerInstrumentation implements TypeInstrumentation {
  private final InstrumentationTarget target;

  public UniversalHandlerInstrumentation(InstrumentationTarget target) {
    this.target = target;
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    String className = target.getFullClassName();

    // Use stored class type data instead of guessing
    switch (target.getClassType()) {
      case INTERFACE:
        return implementsInterface(named(className));
      case ABSTRACT:
        return hasSuperType(named(className))
            .and(not(net.bytebuddy.matcher.ElementMatchers.isInterface()));
      case CONCRETE:
        return named(className);
    }
    // This should never be reached, but required for compilation
    throw new AssertionError("Unexpected class type: " + target.getClassType());
  }

  @Override
  public void transform(TypeTransformer transformer) {
    // Use the appropriate advice class based on handler argument index and class name
    String adviceClassName;
    
    // Special case for RESTEasy VertxRequestHandler
    if (target.getClassName().equals("VertxRequestHandler") && target.getHandlerArgIndex() == 0) {
      adviceClassName = this.getClass().getName() + "$ResteasyAdvice";
    } else if (target.getClassName().equals("RouterImpl") && target.getMethodName().equals("handle") && target.getHandlerArgIndex() == 0) {
      // Special case for Vertx Router.handle() method
      adviceClassName = this.getClass().getName() + "$RouterAdvice";
    } else if (target.getPackageName().equals("com.dream11.rest") && target.getClassName().equals("AbstractRoute") && target.getMethodName().equals("handle") && target.getNumberOfArgs() == 1 && target.getHandlerArgIndex() == 0) {
      // Special case for AbstractRoute.handle(RoutingContext) method (concrete method, not abstract)
      adviceClassName = this.getClass().getName() + "$AbstractRouteAdvice";
    } else {
      switch (target.getHandlerArgIndex()) {
        case -1:
          // Special case: No handler argument, just method instrumentation (e.g., pause method)
          adviceClassName = this.getClass().getName() + "$MethodAdvice";
          break;
        case 0:
          adviceClassName = this.getClass().getName() + "$HandlerAdvice0";
          break;
        case 1:
          adviceClassName = this.getClass().getName() + "$HandlerAdvice1";
          break;
        case 2:
          adviceClassName = this.getClass().getName() + "$HandlerAdvice2";
          break;
        case 3:
          adviceClassName = this.getClass().getName() + "$HandlerAdvice3";
          break;
        default:
          throw new IllegalArgumentException(
              "Unsupported handler argument index: "
                  + target.getHandlerArgIndex()
                  + " for target: "
                  + target);
      }
    }
    
    // Build the method matcher based on whether the method is private
    ElementMatcher.Junction<net.bytebuddy.description.method.MethodDescription> methodMatcher = 
        isMethod().and(named(target.getMethodName())).and(takesArguments(target.getNumberOfArgs()));
    
    // Special case for AbstractRoute.handle() - only match the RoutingContext version (concrete method, not abstract)
    if (target.getPackageName().equals("com.dream11.rest") && target.getClassName().equals("AbstractRoute") && target.getMethodName().equals("handle")) {
      methodMatcher = methodMatcher
          .and(takesArgument(0, named("io.vertx.reactivex.ext.web.RoutingContext")))
          .and(not(isAbstract()));
    }
    
    // Add private matcher if the method is private
    if (target.isPrivate()) {
      methodMatcher = methodMatcher.and(isPrivate());
    }
    
//    System.out.println(
//        "UNIVERSAL-CONTEXT-STORAGE-NEW-TRANSFORM: "
//            + adviceClassName
//            + "\n"
//            + target.getMethodName()
//            + "\n"
//            + target.getNumberOfArgs()
//            + "\n"
//            + "Private: " + target.isPrivate());
    
    transformer.applyAdviceToMethod(methodMatcher, adviceClassName);
  }

  // Advice classes for different handler argument positions
  @SuppressWarnings("unused")
  public static class HandlerAdvice0 {
    private HandlerAdvice0() {}

    @Advice.OnMethodEnter(suppress = Throwable.class)
    @Advice.AssignReturned.ToArguments(@Advice.AssignReturned.ToArguments.ToArgument(0))
    public static Handler<?> onEnter(@Advice.Argument(0) Handler<?> handler) {
//      System.out.println(
//          "UNIVERSAL-WRAP-ARG0: Wrapping handler "
//              + (handler != null ? handler.getClass().getSimpleName() : "null")
//              + " on thread "
//              + Thread.currentThread().getName());
      return UniversalContextPreservingHandler.wrap(handler);
    }
  }

  @SuppressWarnings("unused")
  public static class HandlerAdvice1 {
    private HandlerAdvice1() {}

    @Advice.OnMethodEnter(suppress = Throwable.class)
    @Advice.AssignReturned.ToArguments(@Advice.AssignReturned.ToArguments.ToArgument(1))
    public static Handler<?> onEnter(@Advice.Argument(1) Handler<?> handler) {
//      System.out.println(
//          "UNIVERSAL-WRAP-ARG1: Wrapping handler "
//              + (handler != null ? handler.getClass().getSimpleName() : "null")
//              + " on thread "
//              + Thread.currentThread().getName());
      return UniversalContextPreservingHandler.wrap(handler);
    }
  }

  @SuppressWarnings("unused")
  public static class HandlerAdvice2 {
    private HandlerAdvice2() {}

    @Advice.OnMethodEnter(suppress = Throwable.class)
    @Advice.AssignReturned.ToArguments(@Advice.AssignReturned.ToArguments.ToArgument(2))
    public static Handler<?> onEnter(@Advice.Argument(2) Handler<?> handler) {
//      System.out.println(
//          "UNIVERSAL-WRAP-ARG2: Wrapping handler "
//              + (handler != null ? handler.getClass().getSimpleName() : "null")
//              + " on thread "
//              + Thread.currentThread().getName());
      return UniversalContextPreservingHandler.wrap(handler);
    }
  }

  @SuppressWarnings("unused")
  public static class HandlerAdvice3 {
    private HandlerAdvice3() {}

    @Advice.OnMethodEnter(suppress = Throwable.class)
    @Advice.AssignReturned.ToArguments(@Advice.AssignReturned.ToArguments.ToArgument(3))
    public static Handler<?> onEnter(@Advice.Argument(3) Handler<?> handler) {
//      System.out.println(
//          "UNIVERSAL-WRAP-ARG3: Wrapping handler "
//              + (handler != null ? handler.getClass().getSimpleName() : "null")
//              + " on thread "
//              + Thread.currentThread().getName());
      return UniversalContextPreservingHandler.wrap(handler);
    }
  }

  // Special advice for methods without handler arguments (e.g., pause method)
  @SuppressWarnings("unused")
  public static class MethodAdvice {
    private MethodAdvice() {}

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(@Advice.This Object target) {
      // Get current OpenTelemetry context
      io.opentelemetry.context.Context currentContext = io.opentelemetry.context.Context.current();
      
//      System.out.println("UNIVERSAL-METHOD-ENTER: " + target.getClass().getSimpleName() + " - Context: " + currentContext);
      
      // Special handling for HttpServerRequest.pause() method
      if (target instanceof io.vertx.core.http.HttpServerRequest) {
        io.vertx.core.http.HttpServerRequest request = (io.vertx.core.http.HttpServerRequest) target;
        
        // Inject custom header
//        request.headers().set("whos.the.sexy", "supermanu");
        
//        System.out.println("UNIVERSAL-HEADER-INJECTION: " + request.uri() + " - Context: " + currentContext);
      }
    }
  }

  // Special advice for RESTEasy VertxRequestHandler.handle() method
  @SuppressWarnings("unused")
  public static class ResteasyAdvice {
    private ResteasyAdvice() {}

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(@Advice.Argument(0) io.vertx.core.http.HttpServerRequest request) {
//      System.out.println("RESTEASY-HANDLER-ENTER: Processing request: " + request.uri());
      
      // Extract the injected traceId from header
      String traceId = request.getHeader("otel.injected_trace_context");
//      System.out.println("RESTEASY-CONTEXT-EXTRACTION: Injected traceId from header: " + traceId);
      
      if (traceId != null && !traceId.isEmpty()) {
        // Try to extract context from Vertx context using the traceId as key
        io.vertx.core.Context vertxContext = io.vertx.core.Vertx.currentContext();
        if (vertxContext != null) {
          // Look for stored context with the traceId as key
          io.opentelemetry.context.Context storedContext = vertxContext.get("otel.context." + traceId);
          
          if (storedContext != null) {
//            System.out.println("RESTEASY-CONTEXT-RESTORED: Found stored context for traceId " + traceId + ": " + storedContext);
            // Set the context as current
            try (io.opentelemetry.context.Scope scope = storedContext.makeCurrent()) {
//              System.out.println("RESTEASY-CONTEXT-ACTIVE: Context is now active");
              // Store in standard otel.context key for other handlers
              vertxContext.put("otel.context", storedContext);
            }
          } else {
//            System.out.println("RESTEASY-CONTEXT-NOT-FOUND: No stored context found for traceId: " + traceId);
          }
        } else {
//          System.out.println("RESTEASY-NO-VERTX-CONTEXT: No Vertx context available");
        }
      } else {
//        System.out.println("RESTEASY-NO-INJECTED-CONTEXT: No injected trace context found in headers");
      }
    }
  }

  // Special advice for Vertx Router.handle() method
  @SuppressWarnings("unused")
  public static class RouterAdvice {
    private RouterAdvice() {}

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(@Advice.Argument(0) io.vertx.core.http.HttpServerRequest request) {
//      System.out.println("ROUTER-HANDLER-ENTER: Processing request: " + request.uri());
      
      // Extract the injected traceId from header
      String traceId = request.getHeader("otel.injected_trace_context");
//      System.out.println("ROUTER-CONTEXT-EXTRACTION: Injected traceId from header: " + traceId);
      
      if (traceId != null && !traceId.isEmpty()) {
        // Try to extract context from Vertx context using the traceId as key
        io.vertx.core.Context vertxContext = io.vertx.core.Vertx.currentContext();
        if (vertxContext != null) {
          // Look for stored context with the traceId as key
          io.opentelemetry.context.Context storedContext = vertxContext.get("otel.context." + traceId);
          
          if (storedContext != null) {
//            System.out.println("ROUTER-CONTEXT-RESTORED: Found stored context for traceId " + traceId + ": " + storedContext);
            // Set the context as current
            try (io.opentelemetry.context.Scope scope = storedContext.makeCurrent()) {
//              System.out.println("ROUTER-CONTEXT-ACTIVE: Context is now active");
              // Store in standard otel.context key for other handlers
              vertxContext.put("otel.context", storedContext);
            }
          } else {
//            System.out.println("ROUTER-CONTEXT-NOT-FOUND: No stored context found for traceId: " + traceId);
          }
        } else {
//          System.out.println("ROUTER-NO-VERTX-CONTEXT: No Vertx context available");
        }
      } else {
//        System.out.println("ROUTER-NO-INJECTED-CONTEXT: No injected trace context found in headers");
      }
    }
  }

  // Special advice for AbstractRoute.handle() method
  @SuppressWarnings("unused")
  public static class AbstractRouteAdvice {
    private AbstractRouteAdvice() {}

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(@Advice.Argument(0) io.vertx.reactivex.ext.web.RoutingContext routingContext) {
//      System.out.println("ABSTRACT-ROUTE-HANDLER-ENTER: Processing route: " + routingContext.request().uri());
      
      // Get the HttpServerRequest from RoutingContext (get delegate from reactivex)
      io.vertx.core.http.HttpServerRequest request = routingContext.request().getDelegate();
      
      // Extract the injected traceId from header
      String traceId = request.getHeader("otel.injected_trace_context");
//      System.out.println("ABSTRACT-ROUTE-CONTEXT-EXTRACTION: Injected traceId from header: " + traceId);
      
      if (traceId != null && !traceId.isEmpty()) {
        // Try to extract context from Vertx context using the traceId as key
        io.vertx.core.Context vertxContext = io.vertx.core.Vertx.currentContext();
        if (vertxContext != null) {
          // Look for stored context with the traceId as key
          io.opentelemetry.context.Context storedContext = vertxContext.get("otel.context." + traceId);
          
          if (storedContext != null) {
//            System.out.println("ABSTRACT-ROUTE-CONTEXT-RESTORED: Found stored context for traceId " + traceId + ": " + storedContext);
            // Set the context as current
            try (io.opentelemetry.context.Scope scope = storedContext.makeCurrent()) {
//              System.out.println("ABSTRACT-ROUTE-CONTEXT-ACTIVE: Context is now active");
              // Store in standard otel.context key for other handlers
              vertxContext.put("otel.context", storedContext);
            }
          } else {
//            System.out.println("ABSTRACT-ROUTE-CONTEXT-NOT-FOUND: No stored context found for traceId: " + traceId);
          }
        } else {
//          System.out.println("ABSTRACT-ROUTE-NO-VERTX-CONTEXT: No Vertx context available");
        }
      } else {
//        System.out.println("ABSTRACT-ROUTE-NO-INJECTED-CONTEXT: No injected trace context found in headers");
      }
    }
  }
}
