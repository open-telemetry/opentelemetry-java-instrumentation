/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.httpurlconnection;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesGetter;
import java.net.HttpURLConnection;
import java.util.List;
import javax.annotation.Nullable;

class HttpUrlHttpAttributesGetter
    implements HttpClientAttributesGetter<HttpURLConnection, Integer> {

  @Override
  public String getHttpRequestMethod(HttpURLConnection connection) {
    return connection.getRequestMethod();
  }

  @Override
  public String getUrlFull(HttpURLConnection connection) {
    return connection.getURL().toExternalForm();
  }

  @Override
  public List<String> getHttpRequestHeader(HttpURLConnection connection, String name) {
    String value = connection.getRequestProperty(name);
    return value == null ? emptyList() : singletonList(value);
  }

  @Override
  public Integer getHttpResponseStatusCode(
      HttpURLConnection connection, Integer statusCode, @Nullable Throwable error) {
    return statusCode;
  }

  @Override
  public List<String> getHttpResponseHeader(
      HttpURLConnection connection, Integer statusCode, String name) {
    String value = connection.getHeaderField(name);
    return value == null ? emptyList() : singletonList(value);
  }
}
