/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.httpurlconnection;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.net.HttpURLConnection;
import java.util.List;
import javax.annotation.Nullable;

class HttpUrlHttpAttributesExtractor
    extends HttpClientAttributesExtractor<HttpURLConnection, Integer> {

  @Override
  protected String method(HttpURLConnection connection) {
    return connection.getRequestMethod();
  }

  @Override
  protected String url(HttpURLConnection connection) {
    return connection.getURL().toExternalForm();
  }

  @Override
  protected List<String> requestHeader(HttpURLConnection connection, String name) {
    String value = connection.getRequestProperty(name);
    return value == null ? emptyList() : singletonList(value);
  }

  @Override
  @Nullable
  protected Long requestContentLength(HttpURLConnection connection, @Nullable Integer statusCode) {
    return null;
  }

  @Override
  @Nullable
  protected Long requestContentLengthUncompressed(
      HttpURLConnection connection, @Nullable Integer response) {
    return null;
  }

  @Override
  protected String flavor(HttpURLConnection connection, @Nullable Integer statusCode) {
    return SemanticAttributes.HttpFlavorValues.HTTP_1_1;
  }

  @Override
  protected Integer statusCode(HttpURLConnection connection, Integer statusCode) {
    return statusCode;
  }

  @Override
  @Nullable
  protected Long responseContentLength(HttpURLConnection connection, Integer statusCode) {
    return null;
  }

  @Override
  @Nullable
  protected Long responseContentLengthUncompressed(
      HttpURLConnection connection, Integer statusCode) {
    return null;
  }

  @Override
  protected List<String> responseHeader(
      HttpURLConnection connection, Integer statusCode, String name) {
    return emptyList();
  }
}
