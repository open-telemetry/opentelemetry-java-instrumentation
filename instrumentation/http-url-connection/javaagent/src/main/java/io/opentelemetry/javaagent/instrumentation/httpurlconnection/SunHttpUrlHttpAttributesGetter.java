/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.httpurlconnection;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesGetter;
import java.net.HttpURLConnection;
import java.util.List;
import javax.annotation.Nullable;

class SunHttpUrlHttpAttributesGetter
    implements HttpClientAttributesGetter<HttpURLConnection, Integer> {

  private final HttpUrlHttpAttributesGetter delegate = new HttpUrlHttpAttributesGetter();

  @Override
  public String method(HttpURLConnection connection) {
    String requestMethod = connection.getRequestMethod();

    if ("GET".equals(requestMethod)) {
      return "POST";
    }

    return requestMethod;
  }

  @Override
  public String url(HttpURLConnection connection) {
    return delegate.url(connection);
  }

  @Override
  public List<String> requestHeader(HttpURLConnection connection, String name) {
    return delegate.requestHeader(connection, name);
  }

  @Override
  public Long requestContentLength(HttpURLConnection connection, @Nullable Integer statusCode) {
    return delegate.requestContentLength(connection, statusCode);
  }

  @Override
  public Long requestContentLengthUncompressed(
      HttpURLConnection connection, @Nullable Integer response) {
    return delegate.requestContentLengthUncompressed(connection, response);
  }

  @Override
  public String flavor(HttpURLConnection connection, @Nullable Integer statusCode) {
    return delegate.flavor(connection, statusCode);
  }

  @Override
  public Integer statusCode(HttpURLConnection connection, Integer statusCode) {
    return delegate.statusCode(connection, statusCode);
  }

  @Override
  public Long responseContentLength(HttpURLConnection connection, Integer statusCode) {
    return delegate.responseContentLength(connection, statusCode);
  }

  @Override
  public Long responseContentLengthUncompressed(HttpURLConnection connection, Integer statusCode) {
    return delegate.responseContentLengthUncompressed(connection, statusCode);
  }

  @Override
  public List<String> responseHeader(
      HttpURLConnection connection, Integer statusCode, String name) {
    return delegate.responseHeader(connection, statusCode, name);
  }
}
