/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rediscala;

import static io.opentelemetry.javaagent.instrumentation.rediscala.RediscalaClientTracer.tracer;

import io.opentelemetry.context.Context;
import scala.runtime.AbstractFunction1;
import scala.util.Try;

public class OnCompleteHandler extends AbstractFunction1<Try<Object>, Void> {
  private final Context context;

  public OnCompleteHandler(Context context) {
    this.context = context;
  }

  @Override
  public Void apply(Try<Object> result) {
    if (result.isFailure()) {
      tracer().endExceptionally(context, result.failed().get());
    } else {
      tracer().end(context);
    }
    return null;
  }
}
