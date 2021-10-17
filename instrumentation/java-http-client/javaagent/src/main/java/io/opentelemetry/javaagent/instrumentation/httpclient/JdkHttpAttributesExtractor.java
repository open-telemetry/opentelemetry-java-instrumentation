/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.httpclient;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import javax.annotation.Nullable;

class JdkHttpAttributesExtractor
    extends HttpClientAttributesExtractor<HttpRequest, HttpResponse<?>> {

  @Override
  protected String method(HttpRequest httpRequest) {
    return httpRequest.method();
  }

  @Override
  protected String url(HttpRequest httpRequest) {
    return httpRequest.uri().toString();
  }

  @Override
  protected List<String> requestHeader(HttpRequest httpRequest, String name) {
    return httpRequest.headers().allValues(name);
  }

  @Override
  @Nullable
  protected Long requestContentLength(
      HttpRequest httpRequest, @Nullable HttpResponse<?> httpResponse) {
    return null;
  }

  @Override
  @Nullable
  protected Long requestContentLengthUncompressed(
      HttpRequest httpRequest, @Nullable HttpResponse<?> httpResponse) {
    return null;
  }

  @Override
  protected Integer statusCode(HttpRequest httpRequest, HttpResponse<?> httpResponse) {
    return httpResponse.statusCode();
  }

  @Override
  protected String flavor(HttpRequest httpRequest, @Nullable HttpResponse<?> httpResponse) {
    if (httpResponse != null && httpResponse.version() == Version.HTTP_2) {
      return SemanticAttributes.HttpFlavorValues.HTTP_2_0;
    }
    return SemanticAttributes.HttpFlavorValues.HTTP_1_1;
  }

  @Override
  @Nullable
  protected Long responseContentLength(
      HttpRequest httpRequest, HttpResponse<?> httpResponse) {
    return null;
  }

  @Override
  @Nullable
  protected Long responseContentLengthUncompressed(
      HttpRequest httpRequest, HttpResponse<?> httpResponse) {
    return null;
  }

  @Override
  protected List<String> responseHeader(
      HttpRequest httpRequest, HttpResponse<?> httpResponse, String name) {
    return httpResponse.headers().allValues(name);
  }
}
