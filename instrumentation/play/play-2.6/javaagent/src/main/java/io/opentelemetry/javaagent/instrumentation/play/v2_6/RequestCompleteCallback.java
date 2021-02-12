/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.play.v2_6;

import static io.opentelemetry.javaagent.instrumentation.play.v2_6.PlayTracer.tracer;

import io.opentelemetry.context.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.api.mvc.Result;
import scala.util.Try;

public class RequestCompleteCallback extends scala.runtime.AbstractFunction1<Try<Result>, Object> {

  private static final Logger log = LoggerFactory.getLogger(RequestCompleteCallback.class);

  private final Context context;

  public RequestCompleteCallback(Context context) {
    this.context = context;
  }

  @Override
  public Object apply(Try<Result> result) {
    try {
      if (result.isFailure()) {
        tracer().endExceptionally(context, result.failed().get());
      } else {
        tracer().end(context);
      }
    } catch (Throwable t) {
      log.debug("error in play instrumentation", t);
    }
    return null;
  }
}
