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

package io.opentelemetry.auto.instrumentation.playws.v2_0;

import static io.opentelemetry.auto.instrumentation.playws.HeadersInjectAdapter.SETTER;
import static io.opentelemetry.auto.instrumentation.playws.PlayWSClientDecorator.DECORATE;
import static io.opentelemetry.auto.instrumentation.playws.PlayWSClientDecorator.TRACER;
import static io.opentelemetry.trace.Span.Kind.CLIENT;
import static io.opentelemetry.trace.TracingContextUtils.withSpan;

import com.google.auto.service.AutoService;
import io.grpc.Context;
import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.instrumentation.playws.BasePlayWSClientInstrumentation;
import io.opentelemetry.auto.tooling.Instrumenter;
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
    public static Span methodEnter(
        @Advice.Argument(0) final Request request,
        @Advice.Argument(value = 1, readOnly = false) AsyncHandler asyncHandler) {

      Span span =
          TRACER.spanBuilder(DECORATE.spanNameForRequest(request)).setSpanKind(CLIENT).startSpan();

      DECORATE.afterStart(span);
      DECORATE.onRequest(span, request);

      Context context = withSpan(span, Context.current());
      OpenTelemetry.getPropagators().getHttpTextFormat().inject(context, request, SETTER);

      if (asyncHandler instanceof StreamedAsyncHandler) {
        asyncHandler = new StreamedAsyncHandlerWrapper((StreamedAsyncHandler) asyncHandler, span);
      } else if (!(asyncHandler instanceof WebSocketUpgradeHandler)) {
        // websocket upgrade handlers aren't supported
        asyncHandler = new AsyncHandlerWrapper(asyncHandler, span);
      }

      return span;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Enter final Span clientSpan, @Advice.Thrown final Throwable throwable) {

      if (throwable != null) {
        DECORATE.onError(clientSpan, throwable);
        DECORATE.beforeFinish(clientSpan);
        clientSpan.end();
      }
    }
  }
}
