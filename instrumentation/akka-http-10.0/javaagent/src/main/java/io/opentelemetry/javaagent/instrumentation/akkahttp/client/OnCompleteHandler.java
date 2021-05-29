/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkahttp.client;

import static io.opentelemetry.javaagent.instrumentation.akkahttp.client.AkkaHttpClientTracer.tracer;

import akka.http.scaladsl.model.HttpResponse;
import io.opentelemetry.context.Context;
import scala.runtime.AbstractFunction1;
import scala.util.Try;

public class OnCompleteHandler extends AbstractFunction1<Try<HttpResponse>, Void> {
  private final Context context;

  public OnCompleteHandler(Context context) {
    this.context = context;
  }

  @Override
  public Void apply(Try<HttpResponse> result) {
    if (result.isSuccess()) {
      tracer().end(context, result.get());
    } else {
      tracer().endExceptionally(context, result.failed().get());
    }
    return null;
  }
}
