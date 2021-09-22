/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet;

import org.checkerframework.checker.nullness.qual.Nullable;

public class ServletRequestContext<T> {
  private final T request;
  private final Object servletOrFilter;

  public ServletRequestContext(T request) {
    this(request, null);
  }

  public ServletRequestContext(T request, Object servletOrFilter) {
    this.request = request;
    this.servletOrFilter = servletOrFilter;
  }

  public T request() {
    return request;
  }

  @Nullable
  public Object servletOrFilter() {
    return servletOrFilter;
  }
}
