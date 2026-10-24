/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.httpserver.v4_0;

import static io.opentelemetry.javaagent.instrumentation.vertx.httpserver.v4_0.VertxHttpServerSingletons.RESPONSE_STATE;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.StreamResetException;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class Http2ServerResponseInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return namedOneOf(
        "io.vertx.core.http.impl.Http2ServerResponseImpl",
        "io.vertx.core.http.impl.Http2ServerResponse");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("write")
            .and(takesArgument(0, named("io.netty.buffer.ByteBuf")))
            .and(takesArgument(1, boolean.class))
            .and(takesArgument(2, named("io.vertx.core.Handler"))),
        getClass().getName() + "$WriteAdvice");
    transformer.applyAdviceToMethod(
        named("sendFile")
            .and(takesArguments(4))
            .and(takesArgument(3, named("io.vertx.core.Handler"))),
        getClass().getName() + "$SendFileAdvice");
    transformer.applyAdviceToMethod(
        named("handleException").and(takesArguments(Throwable.class)),
        getClass().getName() + "$ExceptionAdvice");
    transformer.applyAdviceToMethod(
        named("reset").and(takesArguments(long.class)), getClass().getName() + "$ResetAdvice");
    transformer.applyAdviceToMethod(
        named("handlerWritabilityChanged").and(takesArguments(boolean.class)),
        getClass().getName() + "$WritabilityAdvice");
  }

  @SuppressWarnings("unused")
  public static class WriteAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static ResponseCompletionHandler.WriteScope onEnter(
        @Advice.This HttpServerResponse response) {
      return ResponseCompletionHandler.beginWrite(response);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This HttpServerResponse response,
        @Advice.Enter @Nullable ResponseCompletionHandler.WriteScope scope,
        @Advice.Thrown @Nullable Throwable error) {
      if (scope != null) {
        scope.close();
      }
      if (error != null) {
        VertxHttpServerSingletons.endResponse(response, error);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class SendFileAdvice {
    @Nullable
    @AssignReturned.ToArguments(@ToArgument(3))
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Handler<AsyncResult<Void>> onEnter(
        @Advice.This HttpServerResponse response,
        @Advice.Argument(3) @Nullable Handler<AsyncResult<Void>> handler) {
      VertxHttpServerResponseState state = RESPONSE_STATE.get(response);
      return state == null
          ? handler
          : new ResponseCompletionHandler(response, state.state, handler, false);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This HttpServerResponse response, @Advice.Thrown @Nullable Throwable error) {
      if (error != null) {
        VertxHttpServerSingletons.endResponse(response, error);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class ExceptionAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static void onEnter(
        @Advice.This HttpServerResponse response, @Advice.Argument(0) Throwable error) {
      VertxHttpServerSingletons.endResponse(response, error);
    }
  }

  @SuppressWarnings("unused")
  public static class ResetAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This HttpServerResponse response,
        @Advice.Argument(0) long code,
        @Advice.Return boolean accepted) {
      if (accepted) {
        VertxHttpServerSingletons.endResponse(response, new StreamResetException(code));
      }
    }
  }

  @SuppressWarnings("unused")
  public static class WritabilityAdvice {
    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Scope onEnter(@Advice.This HttpServerResponse response) {
      VertxHttpServerResponseState state = RESPONSE_STATE.get(response);
      return state == null ? null : state.state.context.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.Enter @Nullable Scope scope) {
      if (scope != null) {
        scope.close();
      }
    }
  }
}
