/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.okhttp.v2_2;

import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesGetter;
import java.util.List;
import javax.annotation.Nullable;

final class OkHttp2HttpAttributesGetter implements HttpClientAttributesGetter<Request, Response> {

  @Override
  public String getMethod(Request request) {
    return request.method();
  }

  @Override
  public String getUrl(Request request) {
    return request.urlString();
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
