/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.internal;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class ServletRequestContext<T> {
  private final T request;

  public ServletRequestContext(T request) {
    this.request = request;
  }

  public T request() {
    return request;
  }
}
