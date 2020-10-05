/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.playws.v1_0;

import static io.opentelemetry.instrumentation.auto.playws.PlayWSClientTracer.TRACER;

import com.google.auto.service.AutoService;
import io.grpc.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.auto.playws.BasePlayWSClientInstrumentation;
import io.opentelemetry.javaagent.tooling.Instrumenter;
import io.opentelemetry.trace.Span;
import net.bytebuddy.asm.Advice;
import play.shaded.ahc.org.asynchttpclient.AsyncHandler;
import play.shaded.ahc.org.asynchttpclient.Request;
import play.shaded.ahc.org.asynchttpclient.handler.StreamedAsyncHandler;
import play.shaded.ahc.org.asynchttpclient.ws.WebSocketUpgradeHandler;

@AutoService(Instrumenter.class)
public class PlayWSClientInstrumentation extends BasePlayWSClientInstrumentation {
  public static class ClientAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.Argument(0) Request request,
        @Advice.Argument(value = 1, readOnly = false) AsyncHandler asyncHandler,
        @Advice.Local("otelSpan") Span span,
        @Advice.Local("otelScope") Scope scope) {
      Context parentContext = Context.current();

      span = TRACER.startSpan(request);
      scope = TRACER.startScope(span, request.getHeaders());

      if (asyncHandler instanceof StreamedAsyncHandler) {
        asyncHandler =
            new StreamedAsyncHandlerWrapper(
                (StreamedAsyncHandler) asyncHandler, span, parentContext);
      } else if (!(asyncHandler instanceof WebSocketUpgradeHandler)) {
        // websocket upgrade handlers aren't supported
        asyncHandler = new AsyncHandlerWrapper(asyncHandler, span, parentContext);
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelSpan") Span span,
        @Advice.Local("otelScope") Scope scope) {
      scope.close();

      if (throwable != null) {
        TRACER.endExceptionally(span, throwable);
      }
    }
  }
}
