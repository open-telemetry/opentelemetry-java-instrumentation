/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.httpurlconnection;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.net.HttpURLConnection;
import org.checkerframework.checker.nullness.qual.Nullable;

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
  protected @Nullable String target(HttpURLConnection connection) {
    return null;
  }

  @Override
  protected @Nullable String host(HttpURLConnection connection) {
    return null;
  }

  @Override
  protected @Nullable String scheme(HttpURLConnection connection) {
    return null;
  }

  @Override
  protected @Nullable String userAgent(HttpURLConnection connection) {
    return connection.getRequestProperty("User-Agent");
  }

  @Override
  protected @Nullable Long requestContentLength(
      HttpURLConnection connection, @Nullable Integer statusCode) {
    return null;
  }

  @Override
  protected @Nullable Long requestContentLengthUncompressed(
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
  protected @Nullable Long responseContentLength(HttpURLConnection connection, Integer statusCode) {
    return null;
  }

  @Override
  protected @Nullable Long responseContentLengthUncompressed(
      HttpURLConnection connection, Integer statusCode) {
    return null;
  }
}
