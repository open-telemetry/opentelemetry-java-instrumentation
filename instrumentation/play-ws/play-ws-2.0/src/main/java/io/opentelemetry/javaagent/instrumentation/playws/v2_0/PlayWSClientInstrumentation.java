/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.playws.v2_0;

import static io.opentelemetry.javaagent.instrumentation.playws.PlayWSClientTracer.tracer;

import com.google.auto.service.AutoService;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.instrumentation.playws.BasePlayWSClientInstrumentation;
import io.opentelemetry.javaagent.tooling.Instrumenter;
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
        @Advice.Local("otelSpan") Span span) {

      span = tracer().startSpan(request);
      // TODO (trask) expose inject separate from startScope, e.g. for async cases
      Scope scope = tracer().startScope(span, request.getHeaders());
      scope.close();

      if (asyncHandler instanceof StreamedAsyncHandler) {
        asyncHandler = new StreamedAsyncHandlerWrapper((StreamedAsyncHandler) asyncHandler, span);
      } else if (!(asyncHandler instanceof WebSocketUpgradeHandler)) {
        // websocket upgrade handlers aren't supported
        asyncHandler = new AsyncHandlerWrapper(asyncHandler, span);
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Thrown Throwable throwable, @Advice.Local("otelSpan") Span span) {

      if (throwable != null) {
        tracer().endExceptionally(span, throwable);
      }
    }
  }
}
