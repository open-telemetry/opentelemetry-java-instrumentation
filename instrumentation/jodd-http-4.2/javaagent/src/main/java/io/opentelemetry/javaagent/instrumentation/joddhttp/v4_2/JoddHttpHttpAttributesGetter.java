/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.joddhttp.v4_2;

import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.HttpFlavorValues.HTTP_1_0;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.HttpFlavorValues.HTTP_1_1;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.HttpFlavorValues.HTTP_2_0;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.HttpFlavorValues.HTTP_3_0;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesGetter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import jodd.http.HttpRequest;
import jodd.http.HttpResponse;

final class JoddHttpHttpAttributesGetter
    implements HttpClientAttributesGetter<HttpRequest, HttpResponse> {
  private static final Logger logger =
      Logger.getLogger(JoddHttpHttpAttributesGetter.class.getName());
  private static final Set<String> ALLOWED_HTTP_FLAVORS =
      new HashSet<>(Arrays.asList(HTTP_1_0, HTTP_1_1, HTTP_2_0, HTTP_3_0));

  @Override
  public String getMethod(HttpRequest request) {
    return request.method();
  }

  @Override
  public String getUrl(HttpRequest request) {
    return request.url();
  }

  @Override
  public List<String> getRequestHeader(HttpRequest request, String name) {
    return request.headers(name);
  }

  @Override
  public Integer getStatusCode(
      HttpRequest request, HttpResponse response, @Nullable Throwable error) {
    return response.statusCode();
  }

  @Override
  @Nullable
  public String getFlavor(HttpRequest request, @Nullable HttpResponse response) {
    String httpVersion = request.httpVersion();
    if (httpVersion == null && response != null) {
      httpVersion = response.httpVersion();
    }
    if (httpVersion != null) {
      if (httpVersion.contains("/")) {
        httpVersion = httpVersion.substring(httpVersion.lastIndexOf("/") + 1);
      }

      if (ALLOWED_HTTP_FLAVORS.contains(httpVersion)) {
        return httpVersion;
      }
    }
    logger.log(Level.FINE, "unexpected http protocol version: {0}", httpVersion);
    return null;
  }

  @Override
  public List<String> getResponseHeader(HttpRequest request, HttpResponse response, String name) {
    return response.headers(name);
  }
}
