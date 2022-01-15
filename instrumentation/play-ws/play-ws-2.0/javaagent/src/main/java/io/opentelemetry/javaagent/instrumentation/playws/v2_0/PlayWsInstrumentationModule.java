/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.playws.v2_0;

import static io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.playws.PlayWsClientSingletons.instrumenter;
import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.instrumentation.playws.AbstractBootstrapInstrumentation;
import io.opentelemetry.javaagent.instrumentation.playws.AsyncHttpClientInstrumentation;
import io.opentelemetry.javaagent.instrumentation.playws.HandlerPublisherInstrumentation;
import java.util.List;
import net.bytebuddy.asm.Advice;
import play.shaded.ahc.org.asynchttpclient.AsyncHandler;
import play.shaded.ahc.org.asynchttpclient.Request;
import play.shaded.ahc.org.asynchttpclient.handler.StreamedAsyncHandler;
import play.shaded.ahc.org.asynchttpclient.ws.WebSocketUpgradeHandler;

@AutoService(InstrumentationModule.class)
public class PlayWsInstrumentationModule extends InstrumentationModule {
  public PlayWsInstrumentationModule() {
    super("play-ws", "play-ws-2.0");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new AsyncHttpClientInstrumentation(this.getClass().getName() + "$ClientAdvice"),
        new HandlerPublisherInstrumentation(),
        new AbstractBootstrapInstrumentation());
  }

  @SuppressWarnings("unused")
  public static class ClientAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.Argument(0) Request request,
        @Advice.Argument(value = 1, readOnly = false) AsyncHandler<?> asyncHandler,
        @Advice.Local("otelContext") Context context) {
      Context parentContext = currentContext();
      if (!instrumenter().shouldStart(parentContext, request)) {
        return;
      }

      context = instrumenter().start(parentContext, request);

      if (asyncHandler instanceof StreamedAsyncHandler) {
        asyncHandler =
            new StreamedAsyncHandlerWrapper(
                (StreamedAsyncHandler<?>) asyncHandler, request, context, parentContext);
      } else if (!(asyncHandler instanceof WebSocketUpgradeHandler)) {
        // websocket upgrade handlers aren't supported
        asyncHandler = new AsyncHandlerWrapper(asyncHandler, request, context, parentContext);
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Argument(0) Request request,
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelContext") Context context) {
      if (context != null && throwable != null) {
        instrumenter().end(context, request, null, throwable);
      }
    }
  }
}
