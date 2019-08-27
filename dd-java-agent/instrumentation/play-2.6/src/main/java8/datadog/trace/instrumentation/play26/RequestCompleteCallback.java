package datadog.trace.instrumentation.play26;

import static datadog.trace.instrumentation.play26.PlayHttpServerDecorator.DECORATE;

import datadog.trace.context.TraceScope;
import io.opentracing.Span;
import io.opentracing.util.GlobalTracer;
import lombok.extern.slf4j.Slf4j;
import play.api.mvc.Result;
import scala.util.Try;

@Slf4j
public class RequestCompleteCallback extends scala.runtime.AbstractFunction1<Try<Result>, Object> {
  private final Span span;

  public RequestCompleteCallback(final Span span) {
    this.span = span;
  }

  @Override
  public Object apply(final Try<Result> result) {
    try {
      if (result.isFailure()) {
        DECORATE.onError(span, result.failed().get());
      } else {
        DECORATE.onResponse(span, result.get());
      }
      DECORATE.beforeFinish(span);
      if (GlobalTracer.get().scopeManager().active() instanceof TraceScope) {
        ((TraceScope) GlobalTracer.get().scopeManager().active()).setAsyncPropagation(false);
      }
    } catch (final Throwable t) {
      log.debug("error in play instrumentation", t);
    } finally {
      span.finish();
    }
    return null;
  }
}
