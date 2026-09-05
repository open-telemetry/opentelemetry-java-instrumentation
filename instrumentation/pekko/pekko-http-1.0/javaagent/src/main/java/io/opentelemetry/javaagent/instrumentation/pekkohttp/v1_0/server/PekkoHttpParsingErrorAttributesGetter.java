/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.server;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import io.opentelemetry.instrumentation.api.semconv.http.HttpServerAttributesGetter;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.pekko.http.javadsl.model.HttpResponse;
import org.apache.pekko.http.scaladsl.model.ErrorInfo;

/**
 * The request failed to parse and nothing of it was recovered, so only the response side is
 * described.
 */
class PekkoHttpParsingErrorAttributesGetter
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
    return response
        .getHeader(name)
        .map(header -> singletonList(header.value()))
        .orElse(emptyList());
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
