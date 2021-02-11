/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v5_0;

import static io.opentelemetry.javaagent.instrumentation.apachehttpclient.v5_0.ApacheHttpClientTracer.tracer;

import io.opentelemetry.context.Context;
import java.io.IOException;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;

public class WrappingStatusSettingResponseHandler<T> implements HttpClientResponseHandler<T> {
  final Context context;
  final HttpClientResponseHandler<T> handler;

  public WrappingStatusSettingResponseHandler(
      Context context, HttpClientResponseHandler<T> handler) {
    this.context = context;
    this.handler = handler;
  }

  @Override
  public T handleResponse(ClassicHttpResponse response) throws IOException, HttpException {
    tracer().end(context, response);
    return handler.handleResponse(response);
  }
}
