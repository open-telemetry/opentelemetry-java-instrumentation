/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet;

import io.opentelemetry.context.propagation.TextMapGetter;

public class ServletRequestGetter<REQUEST>
    implements TextMapGetter<ServletRequestContext<REQUEST>> {
  protected final ServletAccessor<REQUEST, ?> accessor;

  public ServletRequestGetter(ServletAccessor<REQUEST, ?> accessor) {
    this.accessor = accessor;
  }

  @Override
  public Iterable<String> keys(ServletRequestContext<REQUEST> carrier) {
    return accessor.getRequestHeaderNames(carrier.request());
  }

  @Override
  public String get(ServletRequestContext<REQUEST> carrier, String key) {
    return accessor.getRequestHeader(carrier.request(), key);
  }
}
