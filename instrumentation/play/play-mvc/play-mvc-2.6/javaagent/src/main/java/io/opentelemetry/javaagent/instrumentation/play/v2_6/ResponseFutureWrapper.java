/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.play.v2_6;

import static io.opentelemetry.javaagent.instrumentation.play.v2_6.Play26Singletons.instrumenter;
import static java.util.logging.Level.FINE;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.context.Context;
import java.util.logging.Logger;
import play.api.mvc.Result;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;
import scala.runtime.AbstractFunction1;

public class ResponseFutureWrapper {

  private static final Logger logger = Logger.getLogger(ResponseFutureWrapper.class.getName());

  public static Future<Result> wrap(
      Future<Result> future, Context context, ExecutionContext executionContext) {

    return future.transform(
        new AbstractFunction1<Result, Result>() {
          @Override
          @CanIgnoreReturnValue
          public Result apply(Result result) {
            try {
              instrumenter().end(context, null, null, null);
            } catch (Throwable t) {
              logger.log(FINE, "error in play instrumentation", t);
            }
            return result;
          }
        },
        new AbstractFunction1<Throwable, Throwable>() {
          @Override
          @CanIgnoreReturnValue
          public Throwable apply(Throwable throwable) {
            try {
              instrumenter().end(context, null, null, throwable);
            } catch (Throwable t) {
              logger.log(FINE, "error in play instrumentation", t);
            }
            return throwable;
          }
        },
        executionContext);
  }

  private ResponseFutureWrapper() {}
}
