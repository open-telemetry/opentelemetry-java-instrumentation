/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ratpack;

import io.opentelemetry.context.propagation.TextMapGetter;
import javax.annotation.Nullable;
import ratpack.http.Request;

enum RatpackGetter implements TextMapGetter<Request> {
  INSTANCE;

  @Override
  public Iterable<String> keys(Request request) {
    return request.getHeaders().getNames();
  }

  @Nullable
  @Override
  public String get(@Nullable Request request, String key) {
    if (request == null) {
      return null;
    }
    return request.getHeaders().get(key);
  }
}
