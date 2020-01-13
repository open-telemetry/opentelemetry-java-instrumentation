package io.opentelemetry.auto.instrumentation.play24;

import static io.opentelemetry.auto.instrumentation.api.AgentTracer.activateSpan;
import static io.opentelemetry.auto.instrumentation.api.AgentTracer.activeSpan;
import static io.opentelemetry.auto.instrumentation.api.AgentTracer.propagate;
import static io.opentelemetry.auto.instrumentation.api.AgentTracer.startSpan;
import static io.opentelemetry.auto.instrumentation.play24.PlayHeaders.GETTER;
import static io.opentelemetry.auto.instrumentation.play24.PlayHttpServerDecorator.DECORATE;

import io.opentelemetry.auto.instrumentation.api.AgentScope;
import io.opentelemetry.auto.instrumentation.api.AgentSpan;
import io.opentelemetry.auto.instrumentation.api.AgentSpan.Context;
import io.opentelemetry.auto.instrumentation.api.Tags;
import net.bytebuddy.asm.Advice;
import play.api.mvc.Action;
import play.api.mvc.Request;
import play.api.mvc.Result;
import scala.concurrent.Future;

public class PlayAdvice {
  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static AgentScope onEnter(@Advice.Argument(0) final Request req) {
    final AgentSpan span;
    if (activeSpan() == null) {
      final Context extractedContext = propagate().extract(req.headers(), GETTER);
      span = startSpan("play.request", extractedContext);
    } else {
      // An upstream framework (e.g. akka-http, netty) has already started the span.
      // Do not extract the context.
      span = startSpan("play.request");
    }
    DECORATE.afterStart(span);
    DECORATE.onConnection(span, req);

    return activateSpan(span, false);
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopTraceOnResponse(
      @Advice.Enter final AgentScope playControllerScope,
      @Advice.This final Object thisAction,
      @Advice.Thrown final Throwable throwable,
      @Advice.Argument(0) final Request req,
      @Advice.Return(readOnly = false) final Future<Result> responseFuture) {
    final AgentSpan playControllerSpan = playControllerScope.span();

    // Call onRequest on return after tags are populated.
    DECORATE.onRequest(playControllerSpan, req);

    if (throwable == null) {
      responseFuture.onComplete(
          new RequestCompleteCallback(playControllerSpan),
          ((Action) thisAction).executionContext());
    } else {
      DECORATE.onError(playControllerSpan, throwable);
      playControllerSpan.setAttribute(Tags.HTTP_STATUS, 500);
      playControllerSpan.setError(true);
      DECORATE.beforeFinish(playControllerSpan);
      playControllerSpan.finish();
    }
    playControllerScope.close();

    final AgentSpan rootSpan = activeSpan();
    // set the resource name on the upstream akka/netty span
    DECORATE.onRequest(rootSpan, req);
  }

  // Unused method for muzzle to allow only 2.4-2.5
  public static void muzzleCheck() {
    play.libs.Akka.system();
  }
}
