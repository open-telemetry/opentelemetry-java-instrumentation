/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkahttp.v10_0.client;

import static io.opentelemetry.javaagent.instrumentation.akkahttp.v10_0.client.AkkaHttpClientSingletons.instrumenter;

import akka.http.scaladsl.model.HttpRequest;
import akka.http.scaladsl.model.HttpResponse;
import io.opentelemetry.context.Context;
import javax.annotation.Nullable;
import scala.runtime.AbstractFunction1;
import scala.util.Try;

public class OnCompleteHandler extends AbstractFunction1<Try<HttpResponse>, Void> {
  private final Context context;
  private final HttpRequest request;

  public OnCompleteHandler(Context context, HttpRequest request) {
    this.context = context;
    this.request = request;
  }

  @Nullable
  @Override
  public Void apply(Try<HttpResponse> result) {
    if (result.isSuccess()) {
      instrumenter().end(context, request, result.get(), null);
    } else {
      instrumenter().end(context, request, null, result.failed().get());
    }
    return null;
  }
}
