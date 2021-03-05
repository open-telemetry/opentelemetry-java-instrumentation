/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.play.v2_6;

import static io.opentelemetry.javaagent.instrumentation.play.v2_6.PlayTracer.tracer;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.tracer.ServerSpan;
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;
import net.bytebuddy.asm.Advice;
import play.api.mvc.Action;
import play.api.mvc.Request;
import play.api.mvc.Result;
import scala.concurrent.Future;

public class PlayAdvice {
  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void onEnter(
      @Advice.Argument(0) Request<?> req,
      @Advice.Local("otelContext") Context context,
      @Advice.Local("otelScope") Scope scope) {
    context = tracer().startSpan("play.request", SpanKind.INTERNAL);
    scope = context.makeCurrent();
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopTraceOnResponse(
      @Advice.This Object thisAction,
      @Advice.Thrown Throwable throwable,
      @Advice.Argument(0) Request<?> req,
      @Advice.Return(readOnly = false) Future<Result> responseFuture,
      @Advice.Local("otelContext") Context context,
      @Advice.Local("otelScope") Scope scope) {
    // Call onRequest on return after tags are populated.
    tracer().updateSpanName(Java8BytecodeBridge.spanFromContext(context), req);
    // set the span name on the upstream akka/netty span
    tracer().updateSpanName(ServerSpan.fromContextOrNull(context), req);

    scope.close();
    // span finished in RequestCompleteCallback
    if (throwable == null) {
      responseFuture.onComplete(
          new RequestCompleteCallback(context), ((Action<?>) thisAction).executionContext());
    } else {
      tracer().endExceptionally(context, throwable);
    }
  }
}
