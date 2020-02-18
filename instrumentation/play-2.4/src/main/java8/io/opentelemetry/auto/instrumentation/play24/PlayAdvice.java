package io.opentelemetry.auto.instrumentation.play24;

import static io.opentelemetry.auto.instrumentation.play24.PlayHeaders.GETTER;
import static io.opentelemetry.auto.instrumentation.play24.PlayHttpServerDecorator.DECORATE;
import static io.opentelemetry.auto.instrumentation.play24.PlayHttpServerDecorator.TRACER;

import io.opentelemetry.auto.instrumentation.api.SpanWithScope;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.SpanContext;
import net.bytebuddy.asm.Advice;
import play.api.mvc.Action;
import play.api.mvc.Request;
import play.api.mvc.Result;
import scala.concurrent.Future;

public class PlayAdvice {
  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static SpanWithScope onEnter(@Advice.Argument(0) final Request req) {
    final Span.Builder spanBuilder = TRACER.spanBuilder("play.request");
    if (!TRACER.getCurrentSpan().getContext().isValid()) {
      try {
        final SpanContext extractedContext =
            TRACER.getHttpTextFormat().extract(req.headers(), GETTER);
        spanBuilder.setParent(extractedContext);
      } catch (final IllegalArgumentException e) {
        // Couldn't extract a context. We should treat this as a root span.
        spanBuilder.setNoParent();
      }
    } else {
      // An upstream framework (e.g. akka-http, netty) has already started the span.
      // Do not extract the context.
    }
    final Span span = spanBuilder.startSpan();
    DECORATE.afterStart(span);
    DECORATE.onConnection(span, req);

    return new SpanWithScope(span, TRACER.withSpan(span));
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopTraceOnResponse(
      @Advice.Enter final SpanWithScope playControllerScope,
      @Advice.This final Object thisAction,
      @Advice.Thrown final Throwable throwable,
      @Advice.Argument(0) final Request req,
      @Advice.Return(readOnly = false) final Future<Result> responseFuture) {
    final Span playControllerSpan = playControllerScope.getSpan();

    // Call onRequest on return after tags are populated.
    DECORATE.onRequest(playControllerSpan, req);

    if (throwable == null) {
      responseFuture.onComplete(
          new RequestCompleteCallback(playControllerSpan),
          ((Action) thisAction).executionContext());
    } else {
      DECORATE.onError(playControllerSpan, throwable);
      DECORATE.beforeFinish(playControllerSpan);
      playControllerSpan.end();
    }
    playControllerScope.closeScope();

    final Span rootSpan = TRACER.getCurrentSpan();
    // set the resource name on the upstream akka/netty span
    DECORATE.onRequest(rootSpan, req);
  }

  // Unused method for muzzle to allow only 2.4-2.5
  public static void muzzleCheck() {
    play.libs.Akka.system();
  }
}
