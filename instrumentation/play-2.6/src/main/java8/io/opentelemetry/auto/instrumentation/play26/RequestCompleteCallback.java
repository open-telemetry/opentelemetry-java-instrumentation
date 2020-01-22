package io.opentelemetry.auto.instrumentation.play26;

import static io.opentelemetry.auto.instrumentation.play26.PlayHttpServerDecorator.DECORATE;

import io.opentelemetry.auto.instrumentation.api.AgentSpan;
import lombok.extern.slf4j.Slf4j;
import play.api.mvc.Result;
import scala.util.Try;

@Slf4j
public class RequestCompleteCallback extends scala.runtime.AbstractFunction1<Try<Result>, Object> {
  private final AgentSpan span;

  public RequestCompleteCallback(final AgentSpan span) {
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
    } catch (final Throwable t) {
      log.debug("error in play instrumentation", t);
    } finally {
      span.finish();
    }
    return null;
  }
}
