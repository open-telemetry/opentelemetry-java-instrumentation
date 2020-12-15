/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v4_0;

import static io.opentelemetry.javaagent.instrumentation.apachehttpclient.v4_0.ApacheHttpClientTracer.tracer;

import io.opentelemetry.context.Context;
import java.io.IOException;
import org.apache.http.HttpResponse;
import org.apache.http.client.ResponseHandler;

public class WrappingStatusSettingResponseHandler<T> implements ResponseHandler<T> {
  final Context context;
  final ResponseHandler<T> handler;

  public WrappingStatusSettingResponseHandler(Context context, ResponseHandler<T> handler) {
    this.context = context;
    this.handler = handler;
  }

  @Override
  public T handleResponse(HttpResponse response) throws IOException {
    tracer().end(context, response);
    return handler.handleResponse(response);
  }
}
