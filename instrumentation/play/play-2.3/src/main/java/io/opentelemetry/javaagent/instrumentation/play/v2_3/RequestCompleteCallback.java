/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.play.v2_3;

import static io.opentelemetry.javaagent.instrumentation.play.v2_3.PlayTracer.TRACER;

import io.opentelemetry.api.trace.Span;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.api.mvc.Result;
import scala.util.Try;

public class RequestCompleteCallback extends scala.runtime.AbstractFunction1<Try<Result>, Object> {

  private static final Logger log = LoggerFactory.getLogger(RequestCompleteCallback.class);

  private final Span span;

  public RequestCompleteCallback(Span span) {
    this.span = span;
  }

  @Override
  public Object apply(Try<Result> result) {
    try {
      if (result.isFailure()) {
        TRACER.endExceptionally(span, result.failed().get());
      } else {
        TRACER.end(span);
      }
    } catch (Throwable t) {
      log.debug("error in play instrumentation", t);
    }
    return null;
  }
}
