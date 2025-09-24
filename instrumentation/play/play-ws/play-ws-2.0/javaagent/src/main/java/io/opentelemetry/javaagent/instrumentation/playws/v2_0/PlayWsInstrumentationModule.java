/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.playws.v2_0;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.playws.v2_0.PlayWs20Singletons.instrumenter;
import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import io.opentelemetry.javaagent.instrumentation.playws.AbstractBootstrapInstrumentation;
import io.opentelemetry.javaagent.instrumentation.playws.AsyncHttpClientInstrumentation;
import io.opentelemetry.javaagent.instrumentation.playws.HandlerPublisherInstrumentation;
import java.util.List;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
import play.shaded.ahc.org.asynchttpclient.AsyncHandler;
import play.shaded.ahc.org.asynchttpclient.Request;
import play.shaded.ahc.org.asynchttpclient.handler.StreamedAsyncHandler;
import play.shaded.ahc.org.asynchttpclient.ws.WebSocketUpgradeHandler;

@AutoService(InstrumentationModule.class)
public class PlayWsInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {
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

  @Override
  public boolean isIndyReady() {
    return true;
  }

  @SuppressWarnings("unused")
  public static class ClientAdvice {

    @AssignReturned.ToArguments(@ToArgument(value = 1, index = 1))
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Object[] methodEnter(
        @Advice.Argument(0) Request request,
        @Advice.Argument(1) AsyncHandler<?> originalAsyncHandler) {
      AsyncHandler<?> asyncHandler = originalAsyncHandler;
      Context parentContext = currentContext();
      if (!instrumenter().shouldStart(parentContext, request)) {
        return new Object[] {null, asyncHandler};
      }

      Context context = instrumenter().start(parentContext, request);

      if (asyncHandler instanceof StreamedAsyncHandler) {
        asyncHandler =
            new StreamedAsyncHandlerWrapper<>(
                (StreamedAsyncHandler<?>) asyncHandler, request, context, parentContext);
      } else if (!(asyncHandler instanceof WebSocketUpgradeHandler)) {
        // websocket upgrade handlers aren't supported
        asyncHandler = new AsyncHandlerWrapper<>(asyncHandler, request, context, parentContext);
      }
      return new Object[] {context, asyncHandler};
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Argument(0) Request request,
        @Advice.Thrown Throwable throwable,
        @Advice.Enter Object[] enterResult) {
      Context context = (Context) enterResult[0];
      if (context != null && throwable != null) {
        instrumenter().end(context, request, null, throwable);
      }
    }
  }
}
