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

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerResponse;
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
  }

  @SuppressWarnings("unused")
  public static class WriteAdvice {
    @Nullable
    @AssignReturned.ToArguments(@ToArgument(2))
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Handler<AsyncResult<Void>> onEnter(
        @Advice.This HttpServerResponse response,
        @Advice.Argument(1) boolean end,
        @Advice.Argument(2) @Nullable Handler<AsyncResult<Void>> handler) {
      VertxHttpServerResponseState state = RESPONSE_STATE.get(response);
      return end && state != null
          ? new ResponseCompletionHandler(response, state.state, handler)
          : handler;
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
          : new ResponseCompletionHandler(response, state.state, handler);
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
}
