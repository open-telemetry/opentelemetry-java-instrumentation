/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.googlehttpclient;

import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponse;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.List;
import javax.annotation.Nullable;

final class GoogleHttpClientHttpAttributesGetter
    implements HttpClientAttributesGetter<HttpRequest, HttpResponse> {

  @Override
  @Nullable
  public String method(HttpRequest httpRequest) {
    return httpRequest.getRequestMethod();
  }

  @Override
  public String url(HttpRequest httpRequest) {
    return httpRequest.getUrl().build();
  }

  @Override
  public List<String> requestHeader(HttpRequest httpRequest, String name) {
    return httpRequest.getHeaders().getHeaderStringValues(name);
  }

  @Override
  @Nullable
  public Long requestContentLength(HttpRequest httpRequest, @Nullable HttpResponse httpResponse) {
    return null;
  }

  @Override
  @Nullable
  public Long requestContentLengthUncompressed(
      HttpRequest httpRequest, @Nullable HttpResponse httpResponse) {
    return null;
  }

  @Override
  public String flavor(HttpRequest httpRequest, @Nullable HttpResponse httpResponse) {
    return SemanticAttributes.HttpFlavorValues.HTTP_1_1;
  }

  @Override
  public Integer statusCode(HttpRequest httpRequest, HttpResponse httpResponse) {
    return httpResponse.getStatusCode();
  }

  @Override
  @Nullable
  public Long responseContentLength(HttpRequest httpRequest, HttpResponse httpResponse) {
    return null;
  }

  @Override
  @Nullable
  public Long responseContentLengthUncompressed(
      HttpRequest httpRequest, HttpResponse httpResponse) {
    return null;
  }

  @Override
  public List<String> responseHeader(
      HttpRequest httpRequest, HttpResponse httpResponse, String name) {
    return httpResponse.getHeaders().getHeaderStringValues(name);
  }
}
