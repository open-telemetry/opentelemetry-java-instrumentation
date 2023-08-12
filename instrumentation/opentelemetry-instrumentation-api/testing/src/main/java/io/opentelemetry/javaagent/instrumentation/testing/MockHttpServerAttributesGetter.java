/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.testing;

import static java.util.Collections.emptyList;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesGetter;
import java.util.List;
import javax.annotation.Nullable;

// only needed so that HttpServerAttributesExtractor can be added to the HTTP server instrumenter,
// and http.route is properly set
enum MockHttpServerAttributesGetter implements HttpServerAttributesGetter<String, Void> {
  INSTANCE;

  @Override
  public String getHttpRequestMethod(String s) {
    return "GET";
  }

  @Override
  public List<String> getHttpRequestHeader(String s, String name) {
    return emptyList();
  }

  @Nullable
  @Override
  public Integer getHttpResponseStatusCode(String s, Void unused, @Nullable Throwable error) {
    return null;
  }

  @Override
  public List<String> getHttpResponseHeader(String s, Void unused, String name) {
    return emptyList();
  }

  @Nullable
  @Override
  public String getUrlScheme(String s) {
    return null;
  }

  @Nullable
  @Override
  public String getUrlPath(String s) {
    return null;
  }

  @Nullable
  @Override
  public String getUrlQuery(String s) {
    return null;
  }

  @Nullable
  @Override
  public String getServerAddress(String s) {
    return null;
  }

  @Nullable
  @Override
  public Integer getServerPort(String s) {
    return null;
  }
}
