/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.asynchttpclient.v1_9;

import com.ning.http.client.Request;
import com.ning.http.client.Response;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;

final class AsyncHttpClientHttpAttributesGetter
    implements HttpClientAttributesGetter<Request, Response> {

  @Override
  public String getMethod(Request request) {
    return request.getMethod();
  }

  @Override
  public String getUrl(Request request) {
    return request.getUri().toUrl();
  }

  @Override
  public List<String> getRequestHeader(Request request, String name) {
    return request.getHeaders().getOrDefault(name, Collections.emptyList());
  }

  @Override
  public Integer getStatusCode(Request request, Response response, @Nullable Throwable error) {
    return response.getStatusCode();
  }

  @Override
  public String getFlavor(Request request, @Nullable Response response) {
    return SemanticAttributes.HttpFlavorValues.HTTP_1_1;
  }

  @Override
  public List<String> getResponseHeader(Request request, Response response, String name) {
    return response.getHeaders().getOrDefault(name, Collections.emptyList());
  }
}
