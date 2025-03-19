/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ratpack.v1_7.internal;

import io.opentelemetry.context.propagation.internal.ExtendedTextMapGetter;
import java.util.Collections;
import java.util.Iterator;
import javax.annotation.Nullable;
import ratpack.http.Request;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
enum RatpackGetter implements ExtendedTextMapGetter<Request> {
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

  @Override
  public Iterator<String> getAll(@Nullable Request request, String key) {
    if (request == null) {
      return Collections.emptyIterator();
    }
    return request.getHeaders().getAll(key).iterator();
  }
}
