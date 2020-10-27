/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.play.v2_3;

import static io.opentelemetry.javaagent.instrumentation.play.v2_3.PlayTracer.TRACER;

import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import io.opentelemetry.javaagent.instrumentation.api.SpanWithScope;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Span.Kind;
import net.bytebuddy.asm.Advice;
import play.api.mvc.Action;
import play.api.mvc.Headers;
import play.api.mvc.Request;
import play.api.mvc.Result;
import scala.concurrent.Future;

public class PlayAdvice {
  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static SpanWithScope onEnter(@Advice.Argument(0) final Request<?> req) {
    Span span = TRACER.startSpan("play.request", Kind.INTERNAL);

    return new SpanWithScope(span, span.makeCurrent());
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopTraceOnResponse(
      @Advice.Enter SpanWithScope playControllerScope,
      @Advice.This Object thisAction,
      @Advice.Thrown Throwable throwable,
      @Advice.Argument(0) Request<?> req,
      @Advice.Return(readOnly = false) Future<Result> responseFuture) {
    Span playControllerSpan = playControllerScope.getSpan();

    if (throwable == null) {
      responseFuture.onComplete(
          new RequestCompleteCallback(playControllerSpan),
          ((Action<?>) thisAction).executionContext());
    } else {
      TRACER.endExceptionally(playControllerSpan, throwable);
    }
    playControllerScope.closeScope();
    // span finished in RequestCompleteCallback

    // set the span name on the upstream akka/netty span
    TRACER.updateSpanName(BaseTracer.getCurrentServerSpan(), req);
  }

  // With this muzzle prevents this instrumentation from applying on Play 2.4+
  public static void muzzleCheck(Headers headers) {
    headers.get("aKey");
  }
}
