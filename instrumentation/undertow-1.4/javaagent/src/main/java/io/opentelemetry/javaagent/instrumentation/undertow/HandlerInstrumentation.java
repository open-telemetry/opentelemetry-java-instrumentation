/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.undertow;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.instrumentation.undertow.UndertowSingletons.helper;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.undertow.server.HttpServerExchange;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class HandlerInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("io.undertow.server.HttpHandler");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("io.undertow.server.HttpHandler"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("handleRequest")
            .and(takesArgument(0, named("io.undertow.server.HttpServerExchange")))
            .and(isPublic()),
        this.getClass().getName() + "$HandleRequestAdvice");
  }

  @SuppressWarnings("unused")
  public static class HandleRequestAdvice {

    public static class AdviceScope {
      private final Context context;
      private final Scope scope;

      private AdviceScope(Context context, Scope scope) {
        this.context = context;
        this.scope = scope;
      }

      @Nullable
      public static AdviceScope start(HttpServerExchange exchange) {
        Context attachedContext = helper().getServerContext(exchange);
        if (attachedContext != null) {
          if (!helper().sameTrace(Context.current(), attachedContext)) {
            // request processing is dispatched to another thread
            Scope scope = attachedContext.makeCurrent();
            helper().handlerStarted(attachedContext);
            return new AdviceScope(attachedContext, scope);
          }
          return null;
        }

        Context parentContext = Context.current();
        if (!helper().shouldStart(parentContext, exchange)) {
          return null;
        }

        Context context = helper().start(parentContext, exchange);
        Scope scope = context.makeCurrent();

        exchange.addExchangeCompleteListener(new EndSpanListener(context));
        return new AdviceScope(context, scope);
      }

      public void end(HttpServerExchange exchange, @Nullable Throwable throwable) {
        scope.close();

        helper().handlerCompleted(context, throwable, exchange);
      }
    }

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AdviceScope onEnter(@Advice.Argument(0) HttpServerExchange exchange) {
      return AdviceScope.start(exchange);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.Argument(0) HttpServerExchange exchange,
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter @Nullable AdviceScope adviceScope) {
      if (adviceScope != null) {
        adviceScope.end(exchange, throwable);
      }
    }
  }
}
