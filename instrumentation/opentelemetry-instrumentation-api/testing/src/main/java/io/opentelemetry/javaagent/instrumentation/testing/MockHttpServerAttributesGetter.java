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

  @Nullable
  @Override
  public String method(String s) {
    return null;
  }

  @Override
  public List<String> requestHeader(String s, String name) {
    return emptyList();
  }

  @Nullable
  @Override
  public Long requestContentLength(String s, @Nullable Void unused) {
    return null;
  }

  @Nullable
  @Override
  public Long requestContentLengthUncompressed(String s, @Nullable Void unused) {
    return null;
  }

  @Nullable
  @Override
  public Integer statusCode(String s, Void unused) {
    return null;
  }

  @Nullable
  @Override
  public Long responseContentLength(String s, Void unused) {
    return null;
  }

  @Nullable
  @Override
  public Long responseContentLengthUncompressed(String s, Void unused) {
    return null;
  }

  @Override
  public List<String> responseHeader(String s, Void unused, String name) {
    return emptyList();
  }

  @Nullable
  @Override
  public String flavor(String s) {
    return null;
  }

  @Nullable
  @Override
  public String target(String s) {
    return null;
  }

  @Nullable
  @Override
  public String route(String s) {
    return null;
  }

  @Nullable
  @Override
  public String scheme(String s) {
    return null;
  }

  @Nullable
  @Override
  public String serverName(String s) {
    return null;
  }
}
