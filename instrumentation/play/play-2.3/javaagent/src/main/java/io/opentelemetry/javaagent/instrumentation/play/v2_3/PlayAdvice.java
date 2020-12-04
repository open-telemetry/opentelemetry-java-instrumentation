/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.play.v2_3;

import static io.opentelemetry.javaagent.instrumentation.play.v2_3.PlayTracer.tracer;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Span.Kind;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import net.bytebuddy.asm.Advice;
import play.api.mvc.Action;
import play.api.mvc.Headers;
import play.api.mvc.Request;
import play.api.mvc.Result;
import scala.concurrent.Future;

public class PlayAdvice {
  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void onEnter(
      @Advice.Argument(0) final Request<?> req,
      @Advice.Local("otelSpan") Span span,
      @Advice.Local("otelScope") Scope scope) {
    span = tracer().startSpan("play.request", Kind.INTERNAL);
    scope = span.makeCurrent();
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopTraceOnResponse(
      @Advice.This Object thisAction,
      @Advice.Thrown Throwable throwable,
      @Advice.Argument(0) Request<?> req,
      @Advice.Return(readOnly = false) Future<Result> responseFuture,
      @Advice.Local("otelSpan") Span span,
      @Advice.Local("otelScope") Scope scope) {

    scope.close();
    // span finished in RequestCompleteCallback
    if (throwable == null) {
      responseFuture.onComplete(
          new RequestCompleteCallback(span), ((Action<?>) thisAction).executionContext());
    } else {
      tracer().endExceptionally(span, throwable);
    }

    // set the span name on the upstream akka/netty span
    tracer().updateSpanName(BaseTracer.getCurrentServerSpan(), req);
  }

  // With this muzzle prevents this instrumentation from applying on Play 2.4+
  public static void muzzleCheck(Headers headers) {
    headers.get("aKey");
  }
}
