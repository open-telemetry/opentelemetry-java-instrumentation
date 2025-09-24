/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.playws.v1_0;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.playws.v1_0.PlayWs10Singletons.instrumenter;
import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import io.opentelemetry.javaagent.instrumentation.playws.AsyncHttpClientInstrumentation;
import io.opentelemetry.javaagent.instrumentation.playws.HandlerPublisherInstrumentation;
import java.util.List;
import javax.annotation.Nullable;
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
    super("play-ws", "play-ws-1.0");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new AsyncHttpClientInstrumentation(
            PlayWsInstrumentationModule.class.getName() + "$ClientAdvice"),
        new HandlerPublisherInstrumentation());
  }

  @Override
  public boolean isIndyReady() {
    return true;
  }

  @SuppressWarnings("unused")
  public static class ClientAdvice {

    public static class AdviceScope {
      private final Context parentContext;
      private final Context context;
      private final Request request;
      private final Scope scope;

      public AdviceScope(Context parentContext, Request request, Context context, Scope scope) {
        this.parentContext = parentContext;
        this.request = request;
        this.context = parentContext;
        this.scope = scope;
      }

      public static AdviceScope start(Request request) {
        Context parentContext = currentContext();
        if (!instrumenter().shouldStart(parentContext, request)) {
          return null;
        }
        Context context = instrumenter().start(parentContext, request);
        return new AdviceScope(parentContext, request, context, context.makeCurrent());
      }

      public AsyncHandler<?> wrap(AsyncHandler<?> handler) {
        if (handler instanceof StreamedAsyncHandler) {
          return new StreamedAsyncHandlerWrapper<>(
              (StreamedAsyncHandler<?>) handler, request, context, parentContext);
        } else if (!(handler instanceof WebSocketUpgradeHandler)) {
          // websocket upgrade handlers aren't supported
          return new AsyncHandlerWrapper<>(handler, request, context, parentContext);
        }
        return handler;
      }

      public void end(@Nullable Throwable throwable) {
        scope.close();
        if (throwable != null) {
          instrumenter().end(context, request, null, throwable);
        }
      }
    }

    @AssignReturned.ToArguments(@ToArgument(value = 1, index = 1))
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Object[] methodEnter(
        @Advice.Argument(0) Request request, @Advice.Argument(1) AsyncHandler<?> asyncHandler) {
      AdviceScope adviceScope = AdviceScope.start(request);
      if (adviceScope == null) {
        return new Object[] {null, asyncHandler};
      }
      return new Object[] {adviceScope, adviceScope.wrap(asyncHandler)};
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Argument(0) Request request,
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter Object[] enterResult) {

      AdviceScope adviceScope = (AdviceScope) enterResult[0];
      if (adviceScope != null) {
        adviceScope.end(throwable);
      }
    }
  }
}
