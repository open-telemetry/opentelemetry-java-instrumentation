package datadog.trace.instrumentation.play23;

import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activeSpan;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.propagate;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.startSpan;
import static datadog.trace.instrumentation.play23.PlayHttpServerDecorator.DECORATE;

import datadog.trace.bootstrap.instrumentation.api.AgentScope;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan.Context;
import datadog.trace.bootstrap.instrumentation.api.Tags;
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
      final Context extractedContext = propagate().extract(req.headers(), PlayHeaders.GETTER);
      span = startSpan("play.request", extractedContext);
    } else {
      // An upstream framework (e.g. akka-http, netty) has already started the span.
      // Do not extract the context.
      span = startSpan("play.request");
    }
    DECORATE.afterStart(span);
    DECORATE.onConnection(span, req);

    final AgentScope scope = activateSpan(span, false);
    scope.setAsyncPropagation(true);
    return scope;
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
      playControllerSpan.setTag(Tags.HTTP_STATUS, 500);
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
