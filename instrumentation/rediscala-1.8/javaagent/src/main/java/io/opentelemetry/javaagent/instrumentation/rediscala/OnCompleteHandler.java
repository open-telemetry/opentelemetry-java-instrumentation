/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rediscala;

import static io.opentelemetry.javaagent.instrumentation.rediscala.RediscalaSingletons.instrumenter;

import io.opentelemetry.context.Context;
import redis.RedisCommand;
import scala.runtime.AbstractFunction1;
import scala.util.Try;

public final class OnCompleteHandler extends AbstractFunction1<Try<Object>, Void> {
  private final Context context;
  private final RedisCommand<?, ?> request;

  public OnCompleteHandler(Context context, RedisCommand<?, ?> request) {
    this.context = context;
    this.request = request;
  }

  @Override
  public Void apply(Try<Object> result) {
    Throwable error = null;
    if (result.isFailure()) {
      error = result.failed().get();
    }
    instrumenter().end(context, request, null, error);
    return null;
  }
}
