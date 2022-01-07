/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.client;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractor;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import java.util.List;
import javax.annotation.Nullable;

public abstract class AbstractVertxHttpAttributesExtractor
    extends HttpClientAttributesExtractor<HttpClientRequest, HttpClientResponse> {

  @Nullable
  @Override
  protected String flavor(HttpClientRequest request, @Nullable HttpClientResponse response) {
    return null;
  }

  @Override
  protected List<String> requestHeader(HttpClientRequest request, String name) {
    return request.headers().getAll(name);
  }

  @Nullable
  @Override
  protected Long requestContentLength(
      HttpClientRequest request, @Nullable HttpClientResponse response) {
    return null;
  }

  @Nullable
  @Override
  protected Long requestContentLengthUncompressed(
      HttpClientRequest request, @Nullable HttpClientResponse response) {
    return null;
  }

  @Override
  protected Integer statusCode(HttpClientRequest request, HttpClientResponse response) {
    return response.statusCode();
  }

  @Nullable
  @Override
  protected Long responseContentLength(HttpClientRequest request, HttpClientResponse response) {
    return null;
  }

  @Nullable
  @Override
  protected Long responseContentLengthUncompressed(
      HttpClientRequest request, HttpClientResponse response) {
    return null;
  }

  @Override
  protected List<String> responseHeader(
      HttpClientRequest request, HttpClientResponse response, String name) {
    return response.headers().getAll(name);
  }
}
