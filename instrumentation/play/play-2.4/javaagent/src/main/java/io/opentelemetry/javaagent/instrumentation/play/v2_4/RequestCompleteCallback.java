/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.play.v2_4;

import static io.opentelemetry.javaagent.instrumentation.play.v2_4.Play24Singletons.instrumenter;
import static java.util.logging.Level.FINE;

import io.opentelemetry.context.Context;
import java.util.logging.Logger;
import play.api.mvc.Result;
import scala.runtime.AbstractFunction1;
import scala.util.Try;

public class RequestCompleteCallback extends AbstractFunction1<Try<Result>, Object> {

  private static final Logger logger = Logger.getLogger(RequestCompleteCallback.class.getName());

  private final Context context;

  public RequestCompleteCallback(Context context) {
    this.context = context;
  }

  @Override
  public Object apply(Try<Result> result) {
    try {
      instrumenter().end(context, null, null, result.isFailure() ? result.failed().get() : null);
    } catch (Throwable t) {
      logger.log(FINE, "error in play instrumentation", t);
    }
    return null;
  }
}
