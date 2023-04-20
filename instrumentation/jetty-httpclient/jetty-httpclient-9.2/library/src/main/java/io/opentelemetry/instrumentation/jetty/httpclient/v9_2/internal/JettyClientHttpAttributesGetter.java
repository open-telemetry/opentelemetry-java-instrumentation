/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jetty.httpclient.v9_2.internal;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesGetter;
import java.util.List;
import javax.annotation.Nullable;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;

enum JettyClientHttpAttributesGetter implements HttpClientAttributesGetter<Request, Response> {
  INSTANCE;

  @Override
  @Nullable
  public String getMethod(Request request) {
    return request.getMethod();
  }

  @Override
  @Nullable
  public String getUrl(Request request) {
    return request.getURI().toString();
  }

  @Override
  public List<String> getRequestHeader(Request request, String name) {
    return request.getHeaders().getValuesList(name);
  }

  @Override
  public Integer getStatusCode(Request request, Response response, @Nullable Throwable error) {
    return response.getStatus();
  }

  @Override
  public List<String> getResponseHeader(Request request, Response response, String name) {
    return response.getHeaders().getValuesList(name);
  }
}
