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

public class WrappingStatusSettingResponseHandler implements ResponseHandler {
  final Context context;
  final ResponseHandler handler;

  public WrappingStatusSettingResponseHandler(Context context, ResponseHandler handler) {
    this.context = context;
    this.handler = handler;
  }

  @Override
  public Object handleResponse(HttpResponse response) throws IOException {
    if (context != null) {
      tracer()
          .onResponse(
              io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge.spanFromContext(
                  context),
              response);
    }
    return handler.handleResponse(response);
  }
}
