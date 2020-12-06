/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.playws.v1_0;

import static io.opentelemetry.javaagent.instrumentation.playws.PlayWsClientTracer.tracer;
import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.tracer.HttpClientOperation;
import io.opentelemetry.javaagent.instrumentation.playws.AsyncHttpClientInstrumentation;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.List;
import net.bytebuddy.asm.Advice;
import play.shaded.ahc.org.asynchttpclient.AsyncHandler;
import play.shaded.ahc.org.asynchttpclient.Request;
import play.shaded.ahc.org.asynchttpclient.Response;
import play.shaded.ahc.org.asynchttpclient.handler.StreamedAsyncHandler;
import play.shaded.ahc.org.asynchttpclient.ws.WebSocketUpgradeHandler;

@AutoService(InstrumentationModule.class)
public class PlayWsInstrumentationModule extends InstrumentationModule {
  public PlayWsInstrumentationModule() {
    super("play-ws", "play-ws-1.0");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new AsyncHttpClientInstrumentation(ClientAdvice.class.getName()));
  }

  public static class ClientAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.Argument(0) Request request,
        @Advice.Argument(value = 1, readOnly = false) AsyncHandler<?> asyncHandler,
        @Advice.Local("otelOperation") HttpClientOperation<Response> operation,
        @Advice.Local("otelScope") Scope scope) {
      operation = tracer().startOperation(request, request.getHeaders());
      scope = operation.makeCurrent();

      if (asyncHandler instanceof StreamedAsyncHandler) {
        asyncHandler =
            new StreamedAsyncHandlerWrapper<>((StreamedAsyncHandler<?>) asyncHandler, operation);
      } else if (!(asyncHandler instanceof WebSocketUpgradeHandler)) {
        // websocket upgrade handlers aren't supported
        asyncHandler = new AsyncHandlerWrapper<>(asyncHandler, operation);
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelOperation") HttpClientOperation<Response> operation,
        @Advice.Local("otelScope") Scope scope) {
      scope.close();
      if (throwable != null) {
        operation.endExceptionally(throwable);
      }
    }
  }
}
