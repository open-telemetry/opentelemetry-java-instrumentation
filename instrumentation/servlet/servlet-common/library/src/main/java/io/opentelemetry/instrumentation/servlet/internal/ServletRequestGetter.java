/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.internal;

import static java.util.Collections.emptyIterator;

import io.opentelemetry.context.propagation.TextMapGetter;
import java.util.Iterator;
import javax.annotation.Nullable;

class ServletRequestGetter<REQUEST> implements TextMapGetter<ServletRequestContext<REQUEST>> {
  protected final ServletAccessor<REQUEST, ?> accessor;

  public ServletRequestGetter(ServletAccessor<REQUEST, ?> accessor) {
    this.accessor = accessor;
  }

  @Override
  public Iterable<String> keys(ServletRequestContext<REQUEST> carrier) {
    return accessor.getRequestHeaderNames(carrier.request());
  }

  @Override
  @Nullable
  public String get(@Nullable ServletRequestContext<REQUEST> carrier, String key) {
    if (carrier == null) {
      return null;
    }
    return accessor.getRequestHeader(carrier.request(), key);
  }

  @Override
  public Iterator<String> getAll(@Nullable ServletRequestContext<REQUEST> carrier, String key) {
    if (carrier == null) {
      return emptyIterator();
    }
    return accessor.getRequestHeaderValues(carrier.request(), key).iterator();
  }
}
