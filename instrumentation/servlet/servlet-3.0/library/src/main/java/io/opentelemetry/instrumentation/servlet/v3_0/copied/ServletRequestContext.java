/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.v3_0.copied;

import javax.annotation.Nullable;

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
