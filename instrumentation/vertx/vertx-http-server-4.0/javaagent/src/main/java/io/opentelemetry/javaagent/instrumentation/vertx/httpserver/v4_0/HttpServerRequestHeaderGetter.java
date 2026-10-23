/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.httpserver.v4_0;

import static java.util.Collections.emptyIterator;

import io.opentelemetry.context.propagation.TextMapGetter;
import io.vertx.core.http.HttpServerRequest;
import java.util.Iterator;
import javax.annotation.Nullable;

final class HttpServerRequestHeaderGetter implements TextMapGetter<HttpServerRequest> {

  @Override
  public Iterable<String> keys(HttpServerRequest request) {
    return request.headers().names();
  }

  @Nullable
  @Override
  public String get(@Nullable HttpServerRequest request, String key) {
    return request == null ? null : request.getHeader(key);
  }

  @Override
  public Iterator<String> getAll(@Nullable HttpServerRequest request, String key) {
    return request == null ? emptyIterator() : request.headers().getAll(key).iterator();
  }
}
