/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.webflux.v5_0.server;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.instrumentation.spring.webflux.v5_0.server.WebfluxSingletons.httpRouteGetter;
import static io.opentelemetry.javaagent.instrumentation.spring.webflux.v5_0.server.WebfluxSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isAbstract;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRoute;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRouteSource;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.springframework.web.reactive.HandlerResult;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

public class HandlerAdapterInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("org.springframework.web.reactive.HandlerAdapter");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return not(isAbstract())
        .and(implementsInterface(named("org.springframework.web.reactive.HandlerAdapter")));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    // Get the class name being transformed - this requires accessing transformer internals
    // For now, just log that we're transforming
    System.out.println("=== HandlerAdapterInstrumentation.transform() called ===");
    System.out.println("  Applying advice to 'handle' method");
    transformer.applyAdviceToMethod(
        isMethod()
            .and(isPublic())
            .and(named("handle"))
            .and(takesArgument(0, named("org.springframework.web.server.ServerWebExchange")))
            .and(takesArgument(1, Object.class))
            .and(takesArguments(2)),
        this.getClass().getName() + "$HandleAdvice");
    System.out.println("=== HandlerAdapterInstrumentation advice applied ===");
  }

  @SuppressWarnings("unused")
  public static class HandleAdvice {

    public static class AdviceScope {
      private final Context context;
      private final Scope scope;

      private AdviceScope(Context context, Scope scope) {
        this.context = context;
        this.scope = scope;
      }

      @Nullable
      public static AdviceScope enter(ServerWebExchange exchange, Object handler) {
        System.out.println("=== HandlerAdapter.enter() called ===");
        System.out.println(
            "  Handler: " + (handler != null ? handler.getClass().getName() : "null"));
        System.out.println("  Thread: " + Thread.currentThread().getName());

        Context parentContext = Context.current();
        System.out.println("  Parent context: " + parentContext);

        // HttpRouteSource.CONTROLLER has useFirst true, and it will update http.route only once
        // using the last portion of the nested path.
        // HttpRouteSource.NESTED_CONTROLLER has useFirst false, and it will make http.route updated
        // twice: 1st using the last portion of the nested path, 2nd time using the full nested
        // path.
        HttpServerRoute.update(
            parentContext, HttpServerRouteSource.NESTED_CONTROLLER, httpRouteGetter(), exchange);

        if (handler == null) {
          System.out.println("  Handler is null, returning null");
          return null;
        }

        boolean shouldStart = instrumenter().shouldStart(parentContext, handler);
        System.out.println("  shouldStart: " + shouldStart);

        if (!shouldStart) {
          System.out.println("  instrumenter().shouldStart returned false, returning null");
          return null;
        }

        Context context = instrumenter().start(parentContext, handler);
        System.out.println("  Started new context: " + context);
        return new AdviceScope(context, context.makeCurrent());
      }

      public Mono<HandlerResult> exit(
          Throwable throwable,
          ServerWebExchange exchange,
          Object handler,
          Mono<HandlerResult> mono) {
        scope.close();

        if (throwable != null) {
          instrumenter().end(context, handler, null, throwable);
        } else {
          mono = AdviceUtils.wrapMono(mono, context);
          exchange.getAttributes().put(AdviceUtils.CONTEXT, context);
          AdviceUtils.registerOnSpanEnd(exchange, context, handler);
          // span finished by wrapped Mono in DispatcherHandlerInstrumentation
          // the Mono is already wrapped at this point, but doesn't read the ON_SPAN_END until
          // the Mono is resolved, which is after this point
        }
        return mono;
      }
    }

    @Nullable
    @Advice.OnMethodEnter
    public static AdviceScope methodEnter(
        @Advice.Argument(0) ServerWebExchange exchange, @Advice.Argument(1) Object handler) {
      System.out.println("=== HandlerAdapter.methodEnter() CALLED ===");
      AdviceScope scope = AdviceScope.enter(exchange, handler);
      System.out.println("  Returning AdviceScope: " + scope);
      return scope;
    }

    @AssignReturned.ToReturned
    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static Mono<HandlerResult> methodExit(
        @Advice.Return Mono<HandlerResult> mono,
        @Advice.Argument(0) ServerWebExchange exchange,
        @Advice.Argument(1) Object handler,
        @Advice.Thrown Throwable throwable,
        @Advice.Enter @Nullable AdviceScope adviceScope) {

      System.out.println("=== HandlerAdapter.methodExit() CALLED ===");
      System.out.println("  AdviceScope: " + adviceScope);
      System.out.println("  Throwable: " + throwable);

      if (adviceScope == null) {
        System.out.println("  AdviceScope is null, returning original mono");
        return mono;
      }

      Mono<HandlerResult> result = adviceScope.exit(throwable, exchange, handler, mono);
      System.out.println("  Returning wrapped mono");
      return result;
    }
  }
}
