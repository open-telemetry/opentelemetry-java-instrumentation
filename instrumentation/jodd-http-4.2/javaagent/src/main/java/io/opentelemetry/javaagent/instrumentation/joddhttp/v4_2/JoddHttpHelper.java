/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.joddhttp.v4_2;

import static io.opentelemetry.javaagent.instrumentation.joddhttp.v4_2.JoddHttpSingletons.instrumenter;

import io.opentelemetry.context.Context;
import jodd.http.HttpRequest;
import jodd.http.HttpResponse;

public class JoddHttpHelper {

  public static void doMethodExit(
      Context context, HttpRequest request, Object result, Throwable throwable) {
    if (throwable != null) {
      instrumenter().end(context, request, null, throwable);
    } else if (result instanceof HttpResponse) {
      instrumenter().end(context, request, (HttpResponse) result, null);
    }
  }

  private JoddHttpHelper() {}
}
