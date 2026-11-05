/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkahttp.v10_0.server;

import static java.util.Collections.emptyList;

import akka.http.javadsl.model.HttpResponse;
import akka.http.scaladsl.model.ErrorInfo;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerAttributesGetter;
import io.opentelemetry.javaagent.instrumentation.akkahttp.v10_0.AkkaHttpUtil;
import java.util.List;
import javax.annotation.Nullable;

// Rejected requests never become an HttpRequest, so request attributes are unavailable.
class AkkaHttpParsingErrorAttributesGetter
    implements HttpServerAttributesGetter<ErrorInfo, HttpResponse> {

  @Nullable
  @Override
  public String getHttpRequestMethod(ErrorInfo request) {
    return null;
  }

  @Override
  public List<String> getHttpRequestHeader(ErrorInfo request, String name) {
    return emptyList();
  }

  @Override
  public Integer getHttpResponseStatusCode(
      ErrorInfo request, HttpResponse response, @Nullable Throwable error) {
    return response.status().intValue();
  }

  @Override
  public List<String> getHttpResponseHeader(ErrorInfo request, HttpResponse response, String name) {
    return AkkaHttpUtil.responseHeader(response, name);
  }

  @Nullable
  @Override
  public String getUrlScheme(ErrorInfo request) {
    return null;
  }

  @Nullable
  @Override
  public String getUrlPath(ErrorInfo request) {
    return null;
  }

  @Nullable
  @Override
  public String getUrlQuery(ErrorInfo request) {
    return null;
  }
}
