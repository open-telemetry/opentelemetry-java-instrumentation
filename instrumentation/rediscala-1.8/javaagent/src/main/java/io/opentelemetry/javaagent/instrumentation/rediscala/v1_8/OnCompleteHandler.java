/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rediscala.v1_8;

import static io.opentelemetry.javaagent.instrumentation.rediscala.v1_8.RediscalaSingletons.instrumenter;

import io.opentelemetry.context.Context;
import javax.annotation.Nullable;
import scala.runtime.AbstractFunction1;
import scala.util.Try;

class OnCompleteHandler extends AbstractFunction1<Try<Object>, Void> {
  private final Context context;
  private final RediscalaRequest request;

  OnCompleteHandler(Context context, RediscalaRequest request) {
    this.context = context;
    this.request = request;
  }

  @Nullable
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
