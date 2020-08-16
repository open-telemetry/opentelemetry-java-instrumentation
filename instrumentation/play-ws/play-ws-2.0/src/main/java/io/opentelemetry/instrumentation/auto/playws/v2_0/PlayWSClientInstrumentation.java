/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.instrumentation.auto.playws.v2_0;

import static io.opentelemetry.instrumentation.auto.playws.HeadersInjectAdapter.SETTER;
import static io.opentelemetry.instrumentation.auto.playws.PlayWSClientTracer.TRACER;
import static io.opentelemetry.trace.TracingContextUtils.withSpan;

import com.google.auto.service.AutoService;
import io.grpc.Context;
import io.opentelemetry.OpenTelemetry;
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
        @Advice.Argument(0) final Request request,
        @Advice.Argument(value = 1, readOnly = false) AsyncHandler asyncHandler,
        @Advice.Local("otelSpan") Span span) {

      span = TRACER.startSpan(request);
      Context context = withSpan(span, Context.current());
      OpenTelemetry.getPropagators().getHttpTextFormat().inject(context, request, SETTER);

      if (asyncHandler instanceof StreamedAsyncHandler) {
        asyncHandler = new StreamedAsyncHandlerWrapper((StreamedAsyncHandler) asyncHandler, span);
      } else if (!(asyncHandler instanceof WebSocketUpgradeHandler)) {
        // websocket upgrade handlers aren't supported
        asyncHandler = new AsyncHandlerWrapper(asyncHandler, span);
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Thrown final Throwable throwable, @Advice.Local("otelSpan") Span span) {

      if (throwable != null) {
        TRACER.endExceptionally(span, throwable);
      }
    }
  }
}
