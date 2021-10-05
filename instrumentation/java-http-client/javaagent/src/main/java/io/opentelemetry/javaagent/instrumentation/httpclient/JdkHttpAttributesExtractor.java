/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.httpclient;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractor;
import io.opentelemetry.javaagent.instrumentation.api.config.HttpHeadersConfig;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

class JdkHttpAttributesExtractor
    extends HttpClientAttributesExtractor<HttpRequest, HttpResponse<?>> {

  JdkHttpAttributesExtractor() {
    super(HttpHeadersConfig.capturedClientHeaders());
  }

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
  protected @Nullable Long requestContentLength(
      HttpRequest httpRequest, @Nullable HttpResponse<?> httpResponse) {
    return null;
  }

  @Override
  protected @Nullable Long requestContentLengthUncompressed(
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
  protected @Nullable Long responseContentLength(
      HttpRequest httpRequest, HttpResponse<?> httpResponse) {
    return null;
  }

  @Override
  protected @Nullable Long responseContentLengthUncompressed(
      HttpRequest httpRequest, HttpResponse<?> httpResponse) {
    return null;
  }

  @Override
  protected List<String> responseHeader(
      HttpRequest httpRequest, HttpResponse<?> httpResponse, String name) {
    return httpResponse.headers().allValues(name);
  }
}
