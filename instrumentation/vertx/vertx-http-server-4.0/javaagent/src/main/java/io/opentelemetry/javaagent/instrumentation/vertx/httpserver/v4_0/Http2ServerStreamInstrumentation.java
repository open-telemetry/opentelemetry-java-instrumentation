/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.httpserver.v4_0;

import static io.opentelemetry.javaagent.instrumentation.vertx.httpserver.v4_0.VertxHttpServerSingletons.STATE;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class Http2ServerStreamInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("io.vertx.core.http.impl.Http2ServerStream");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("doWriteHeaders")
            .and(takesArgument(0, named("io.netty.handler.codec.http2.Http2Headers")))
            .and(takesArgument(1, boolean.class))
            .and(takesArgument(2, named("io.vertx.core.Handler"))),
        getClass().getName() + "$HeadersAdvice");
  }

  @SuppressWarnings("unused")
  public static class HeadersAdvice {
    @Nullable
    @AssignReturned.ToArguments(@ToArgument(2))
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Handler<AsyncResult<Void>> onEnter(
        @Advice.This Object stream,
        @Advice.Argument(1) boolean end,
        @Advice.Argument(2) @Nullable Handler<AsyncResult<Void>> handler) {
      if (end && stream instanceof HttpServerRequest) {
        HttpServerRequest request = (HttpServerRequest) stream;
        VertxHttpServerState state = STATE.get(request);
        if (state != null) {
          return new ResponseCompletionHandler(request.response(), state, handler);
        }
      }
      return handler;
    }
  }
}
