/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.okhttp.v3_0;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesGetter;
import java.util.List;
import javax.annotation.Nullable;
import okhttp3.Request;
import okhttp3.Response;

enum OkHttpAttributesGetter implements HttpClientAttributesGetter<Request, Response> {
  INSTANCE;

  @Override
  public String getMethod(Request request) {
    return request.method();
  }

  @Override
  public String getUrl(Request request) {
    return request.url().toString();
  }

  @Override
  public List<String> getRequestHeader(Request request, String name) {
    return request.headers(name);
  }

  @Override
  public Integer getStatusCode(Request request, Response response, @Nullable Throwable error) {
    return response.code();
  }

  @Override
  public List<String> getResponseHeader(Request request, Response response, String name) {
    return response.headers(name);
  }
}
