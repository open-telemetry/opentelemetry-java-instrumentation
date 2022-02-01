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
  public String method(Request request) {
    return request.getMethod();
  }

  @Override
  public String url(Request request) {
    return request.getUri().toUrl();
  }

  @Override
  public List<String> requestHeader(Request request, String name) {
    return request.getHeaders().getOrDefault(name, Collections.emptyList());
  }

  @Override
  @Nullable
  public Long requestContentLength(Request request, @Nullable Response response) {
    return null;
  }

  @Override
  @Nullable
  public Long requestContentLengthUncompressed(Request request, @Nullable Response response) {
    return null;
  }

  @Override
  public Integer statusCode(Request request, Response response) {
    return response.getStatusCode();
  }

  @Override
  public String flavor(Request request, @Nullable Response response) {
    return SemanticAttributes.HttpFlavorValues.HTTP_1_1;
  }

  @Override
  @Nullable
  public Long responseContentLength(Request request, Response response) {
    return null;
  }

  @Override
  @Nullable
  public Long responseContentLengthUncompressed(Request request, Response response) {
    return null;
  }

  @Override
  public List<String> responseHeader(Request request, Response response, String name) {
    return response.getHeaders().getOrDefault(name, Collections.emptyList());
  }
}
