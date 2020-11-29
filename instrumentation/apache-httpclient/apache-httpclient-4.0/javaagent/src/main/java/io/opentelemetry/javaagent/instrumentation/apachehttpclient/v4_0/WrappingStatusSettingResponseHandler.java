/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v4_0;

import static io.opentelemetry.javaagent.instrumentation.apachehttpclient.v4_0.ApacheHttpClientTracer.tracer;

import io.opentelemetry.api.trace.Span;
import java.io.IOException;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;

public class WrappingStatusSettingResponseHandler implements ResponseHandler {
  final Span span;
  final ResponseHandler handler;

  public WrappingStatusSettingResponseHandler(Span span, ResponseHandler handler) {
    this.span = span;
    this.handler = handler;
  }

  @Override
  public Object handleResponse(HttpResponse response) throws ClientProtocolException, IOException {
    if (null != span) {
      tracer().onResponse(span, response);
    }
    return handler.handleResponse(response);
  }
}
