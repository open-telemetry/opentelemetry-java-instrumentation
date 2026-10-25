/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jetty.httpclient.v9_2;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.jetty.httpclient.v9_2.JettyContentContext.CallbackScope;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.eclipse.jetty.client.HttpExchange;
import org.eclipse.jetty.client.api.Request;

class JettyHttpSenderInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return namedOneOf(
        "org.eclipse.jetty.client.HttpSender", "org.eclipse.jetty.client.HttpExchange");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isConstructor().and(takesArgument(1, named("org.eclipse.jetty.client.HttpRequest"))),
        getClass().getName() + "$ExchangeConstructorAdvice");
    transformer.applyAdviceToMethod(
        named("send").and(takesArgument(0, named("org.eclipse.jetty.client.HttpExchange"))),
        getClass().getName() + "$SendAdvice");
  }

  @SuppressWarnings("unused")
  public static class ExchangeConstructorAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This HttpExchange exchange, @Advice.Argument(1) Request request) {
      // getRequest() has an incompatible covariant return descriptor starting in Jetty 9.3.
      JettyContentContext.initialize(exchange, request);
    }
  }

  @SuppressWarnings("unused")
  public static class SendAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static SendState onEnter(@Advice.Argument(0) HttpExchange exchange) {
      JettyContentContext caller = JettyContentContext.getCaller(exchange);
      CallbackScope scope = caller == null ? null : caller.open();
      JettyContentContext previous = JettyContentContext.initializing(caller);
      return new SendState(previous, scope);
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
    public static void onExit(@Advice.Enter @Nullable SendState state) {
      if (state != null) {
        JettyContentContext.initializing(state.previous);
        if (state.scope != null) {
          state.scope.close();
        }
      }
    }
  }

  public static final class SendState {
    @Nullable final JettyContentContext previous;
    @Nullable final CallbackScope scope;

    SendState(@Nullable JettyContentContext previous, @Nullable CallbackScope scope) {
      this.previous = previous;
      this.scope = scope;
    }
  }
}
