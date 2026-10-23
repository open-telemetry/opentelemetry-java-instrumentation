/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.httpserver.v4_0;

import static io.opentelemetry.javaagent.instrumentation.vertx.httpserver.v4_0.VertxHttpServerSingletons.STATE;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.StreamResetException;
import java.nio.channels.ClosedChannelException;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class Http2ServerRequestInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return namedOneOf(
        "io.vertx.core.http.impl.Http2ServerRequestImpl",
        "io.vertx.core.http.impl.Http2ServerRequest");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("dispatch").and(takesArgument(0, named("io.vertx.core.Handler"))),
        getClass().getName() + "$DispatchAdvice");
    transformer.applyAdviceToMethod(
        named("handleData")
            .and(takesArgument(0, named("io.vertx.core.buffer.Buffer")))
            .or(named("handleEnd").and(takesArgument(0, named("io.vertx.core.MultiMap"))))
            .or(named("handleClose").and(takesArguments(0))),
        getClass().getName() + "$CallbackAdvice");
    transformer.applyAdviceToMethod(
        named("handleException").and(takesArguments(Throwable.class)),
        getClass().getName() + "$ExceptionAdvice");
    transformer.applyAdviceToMethod(
        named("handleReset").and(takesArguments(long.class)),
        getClass().getName() + "$ResetAdvice");
  }

  @SuppressWarnings("unused")
  public static class DispatchAdvice {
    @AssignReturned.ToArguments(@ToArgument(0))
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Handler<HttpServerRequest> onEnter(
        @Advice.Argument(0) Handler<HttpServerRequest> handler) {
      return handler instanceof ServerRequestHandler ? handler : new ServerRequestHandler(handler);
    }
  }

  @SuppressWarnings("unused")
  public static class CallbackAdvice {
    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Scope onEnter(@Advice.This HttpServerRequest request) {
      VertxHttpServerState state = STATE.get(request);
      return state == null ? null : state.context.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This HttpServerRequest request,
        @Advice.Origin("#m") String method,
        @Advice.Enter @Nullable Scope scope,
        @Advice.Thrown @Nullable Throwable error) {
      if (scope != null) {
        scope.close();
      }
      if (error != null) {
        VertxHttpServerSingletons.end(request, error);
      } else if (method.equals("handleClose")) {
        VertxHttpServerSingletons.end(
            request, request.response().ended() ? null : new ClosedChannelException());
      }
    }
  }

  @SuppressWarnings("unused")
  public static class ExceptionAdvice {
    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Scope onEnter(
        @Advice.This HttpServerRequest request, @Advice.Argument(0) Throwable error) {
      VertxHttpServerState state = STATE.get(request);
      Scope scope = state == null ? null : state.context.makeCurrent();
      VertxHttpServerSingletons.end(request, error);
      return scope;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.Enter @Nullable Scope scope) {
      if (scope != null) {
        scope.close();
      }
    }
  }

  @SuppressWarnings("unused")
  public static class ResetAdvice {
    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Scope onEnter(
        @Advice.This HttpServerRequest request, @Advice.Argument(0) long code) {
      VertxHttpServerState state = STATE.get(request);
      Scope scope = state == null ? null : state.context.makeCurrent();
      VertxHttpServerSingletons.end(request, new StreamResetException(code));
      return scope;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.Enter @Nullable Scope scope) {
      if (scope != null) {
        scope.close();
      }
    }
  }
}
