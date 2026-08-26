/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.server;

import static java.util.Collections.emptyList;

import io.opentelemetry.instrumentation.api.semconv.http.HttpServerAttributesGetter;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.pekko.http.javadsl.model.HttpResponse;

/**
 * The request failed to parse, so the only thing known about it is the response that pekko-http
 * generated. Everything that would come from the request is reported as unknown.
 */
class PekkoHttpParsingErrorAttributesGetter
    implements HttpServerAttributesGetter<PekkoHttpParsingError, HttpResponse> {

  @Nullable
  @Override
  public String getHttpRequestMethod(PekkoHttpParsingError request) {
    return null;
  }

  @Override
  public List<String> getHttpRequestHeader(PekkoHttpParsingError request, String name) {
    return emptyList();
  }

  @Override
  public Integer getHttpResponseStatusCode(
      PekkoHttpParsingError request, HttpResponse response, @Nullable Throwable error) {
    return response.status().intValue();
  }

  @Override
  public List<String> getHttpResponseHeader(
      PekkoHttpParsingError request, HttpResponse response, String name) {
    return emptyList();
  }

  @Nullable
  @Override
  public String getUrlScheme(PekkoHttpParsingError request) {
    return null;
  }

  @Nullable
  @Override
  public String getUrlPath(PekkoHttpParsingError request) {
    return null;
  }

  @Nullable
  @Override
  public String getUrlQuery(PekkoHttpParsingError request) {
    return null;
  }
}
