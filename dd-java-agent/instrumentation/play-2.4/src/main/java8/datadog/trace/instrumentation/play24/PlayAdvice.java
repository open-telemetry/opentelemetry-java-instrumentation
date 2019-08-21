package datadog.trace.instrumentation.play24;

import static datadog.trace.instrumentation.play24.PlayHttpServerDecorator.DECORATE;

import datadog.trace.context.TraceScope;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.propagation.Format;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import net.bytebuddy.asm.Advice;
import play.api.mvc.Action;
import play.api.mvc.Request;
import play.api.mvc.Result;
import scala.concurrent.Future;

public class PlayAdvice {
  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static Scope startSpan(@Advice.Argument(0) final Request req) {
    final Scope scope;
    if (GlobalTracer.get().activeSpan() == null) {
      final SpanContext extractedContext;
      if (GlobalTracer.get().scopeManager().active() == null) {
        extractedContext =
            GlobalTracer.get().extract(Format.Builtin.HTTP_HEADERS, new PlayHeaders(req));
      } else {
        extractedContext = null;
      }
      scope =
          GlobalTracer.get()
              .buildSpan("play.request")
              .asChildOf(extractedContext)
              .startActive(false);
    } else {
      // An upstream framework (e.g. akka-http, netty) has already started the span.
      // Do not extract the context.
      scope = GlobalTracer.get().buildSpan("play.request").startActive(false);
    }
    DECORATE.afterStart(scope);
    DECORATE.onConnection(scope.span(), req);

    if (GlobalTracer.get().scopeManager().active() instanceof TraceScope) {
      ((TraceScope) GlobalTracer.get().scopeManager().active()).setAsyncPropagation(true);
    }
    return scope;
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopTraceOnResponse(
      @Advice.Enter final Scope playControllerScope,
      @Advice.This final Object thisAction,
      @Advice.Thrown final Throwable throwable,
      @Advice.Argument(0) final Request req,
      @Advice.Return(readOnly = false) final Future<Result> responseFuture) {
    final Span playControllerSpan = playControllerScope.span();

    // Call onRequest on return after tags are populated.
    DECORATE.onRequest(playControllerSpan, req);

    if (throwable == null) {
      responseFuture.onComplete(
          new RequestCompleteCallback(playControllerSpan),
          ((Action) thisAction).executionContext());
    } else {
      DECORATE.onError(playControllerSpan, throwable);
      Tags.HTTP_STATUS.set(playControllerSpan, 500);
      DECORATE.beforeFinish(playControllerSpan);
      playControllerSpan.finish();
    }
    playControllerScope.close();

    final Span rootSpan = GlobalTracer.get().activeSpan();
    // set the resource name on the upstream akka/netty span
    DECORATE.onRequest(rootSpan, req);
  }

  // Unused method for muzzle to allow only 2.4-2.5
  public static void muzzleCheck() {
    play.libs.Akka.system();
  }
}
