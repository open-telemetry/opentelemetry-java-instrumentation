/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkahttp.client;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;
import scala.concurrent.impl.Promise;
import scala.runtime.AbstractFunction1;
import scala.util.Try;

public class FutureWrapper {
  public static <T> Future<T> wrap(
      Future<T> future, ExecutionContext executionContext, Context context) {
    Promise.DefaultPromise<T> promise = new Promise.DefaultPromise<>();
    future.onComplete(
        new AbstractFunction1<Try<T>, Object>() {

          @Override
          public Object apply(Try<T> result) {
            try (Scope ignored = context.makeCurrent()) {
              return promise.complete(result);
            }
          }
        },
        executionContext);

    return promise;
  }
}
