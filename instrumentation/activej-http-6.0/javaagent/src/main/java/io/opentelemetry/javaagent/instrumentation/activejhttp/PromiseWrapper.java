/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.activejhttp;

import static io.opentelemetry.javaagent.instrumentation.activejhttp.ActivejHttpServerConnectionSingletons.instrumenter;

import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import io.opentelemetry.context.Context;

public final class PromiseWrapper {

  public static Promise<HttpResponse> wrap(
      Promise<HttpResponse> promise, HttpRequest httpRequest, Context context) {
    return promise.whenComplete(
        (httpResponse, exception) ->
            instrumenter().end(context, httpRequest, httpResponse, exception));
  }

  private PromiseWrapper() {}
}
