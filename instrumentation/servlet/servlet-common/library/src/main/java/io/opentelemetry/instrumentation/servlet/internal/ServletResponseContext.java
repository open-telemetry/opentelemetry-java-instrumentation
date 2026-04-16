/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.internal;

import static java.util.Objects.requireNonNull;

import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class ServletResponseContext<T> {
  @Nullable private final T response;
  // used for servlet 2.2 where request status can't be extracted from HttpServletResponse
  @Nullable private Integer status;
  @Nullable private Long timeout;

  public ServletResponseContext(@Nullable T response) {
    this.response = response;
  }

  @Nullable
  public T response() {
    return response;
  }

  public void setStatus(int status) {
    this.status = status;
  }

  public int getStatus() {
    return requireNonNull(status);
  }

  public boolean hasStatus() {
    return status != null;
  }

  public void setTimeout(long timeout) {
    this.timeout = timeout;
  }

  public long getTimeout() {
    return requireNonNull(timeout);
  }

  public boolean hasTimeout() {
    return timeout != null;
  }
}
