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
  public String getMethod(String s) {
    return "GET";
  }

  @Override
  public List<String> getRequestHeader(String s, String name) {
    return emptyList();
  }

  @Nullable
  @Override
  public Integer getStatusCode(String s, Void unused, @Nullable Throwable error) {
    return null;
  }

  @Override
  public List<String> getResponseHeader(String s, Void unused, String name) {
    return emptyList();
  }

  @Nullable
  @Override
  public String getFlavor(String s) {
    return null;
  }

  @Nullable
  @Override
  public String getTarget(String s) {
    return null;
  }

  @Nullable
  @Override
  public String getScheme(String s) {
    return null;
  }
}
