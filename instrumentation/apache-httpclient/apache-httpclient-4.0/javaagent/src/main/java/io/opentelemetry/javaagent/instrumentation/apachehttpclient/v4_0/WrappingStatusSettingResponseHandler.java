/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v4_0;

import static io.opentelemetry.javaagent.instrumentation.apachehttpclient.v4_0.ApacheHttpClientTracer.tracer;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import java.io.IOException;
import org.apache.http.HttpResponse;
import org.apache.http.client.ResponseHandler;

public class WrappingStatusSettingResponseHandler<T> implements ResponseHandler<T> {
  final Context context;
  final Context parentContext;
  final ResponseHandler<T> handler;

  public WrappingStatusSettingResponseHandler(
      Context context, Context parentContext, ResponseHandler<T> handler) {
    this.context = context;
    this.parentContext = parentContext;
    this.handler = handler;
  }

  @Override
  public T handleResponse(HttpResponse response) throws IOException {
    tracer().end(context, response);
    try (Scope ignored = parentContext.makeCurrent()) {
      return handler.handleResponse(response);
    }
  }
}
