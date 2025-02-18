/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.activejhttp;

import io.activej.http.HttpError;
import io.activej.http.HttpHeader;
import io.activej.http.HttpHeaderValue;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpResponse;
import java.util.Map;

public final class ActiveJHttpServerHelper {

  private ActiveJHttpServerHelper() {
    throw new UnsupportedOperationException();
  }

  public static HttpResponse createResponse(
      Throwable throwable, String traceparent, HttpResponse httpResponse) {
    int code = 500;
    if (httpResponse != null) {
      code = httpResponse.getCode();
    } else if (throwable instanceof HttpError) {
      HttpError error = (HttpError) throwable;
      code = error.getCode();
    }
    HttpResponse.Builder responseBuilder = HttpResponse.ofCode(code);
    if (httpResponse != null) {
      if (httpResponse.hasBody()) {
        responseBuilder.withBody(httpResponse.getBody());
      }
      for (Map.Entry<HttpHeader, HttpHeaderValue> entry : httpResponse.getHeaders()) {
        responseBuilder.withHeader(entry.getKey(), entry.getValue());
      }
    }
    if (throwable != null) {
      responseBuilder.withPlainText(throwable.getMessage());
    }
    return responseBuilder.withHeader(HttpHeaders.of("traceparent"), traceparent).build();
  }
}
