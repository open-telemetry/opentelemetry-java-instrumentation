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

package io.opentelemetry.instrumentation.auto.play.v2_6;

import static io.opentelemetry.instrumentation.auto.play.v2_6.PlayTracer.TRACER;
import static io.opentelemetry.trace.TracingContextUtils.currentContextWith;

import io.opentelemetry.instrumentation.api.tracer.BaseTracerHelper;
import io.opentelemetry.instrumentation.auto.api.SpanWithScope;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Span.Kind;
import net.bytebuddy.asm.Advice;
import play.api.mvc.Action;
import play.api.mvc.Request;
import play.api.mvc.Result;
import scala.concurrent.Future;

public class PlayAdvice {
  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static SpanWithScope onEnter(@Advice.Argument(0) final Request<?> req) {
    Span span = TRACER.startSpan("play.request", Kind.INTERNAL);

    return new SpanWithScope(span, currentContextWith(span));
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopTraceOnResponse(
      @Advice.Enter SpanWithScope playControllerScope,
      @Advice.This Object thisAction,
      @Advice.Thrown Throwable throwable,
      @Advice.Argument(0) Request<?> req,
      @Advice.Return(readOnly = false) Future<Result> responseFuture) {
    Span playControllerSpan = playControllerScope.getSpan();

    // Call onRequest on return after tags are populated.
    TRACER.updateSpanName(playControllerSpan, req);

    if (throwable == null) {
      responseFuture.onComplete(
          new RequestCompleteCallback(playControllerSpan),
          ((Action<?>) thisAction).executionContext());
    } else {
      TRACER.endExceptionally(playControllerSpan, throwable);
    }
    playControllerScope.closeScope();
    // span finished in RequestCompleteCallback

    Span rootSpan = BaseTracerHelper.getCurrentServerSpan();
    // set the span name on the upstream akka/netty span
    TRACER.updateSpanName(rootSpan, req);
  }
}
