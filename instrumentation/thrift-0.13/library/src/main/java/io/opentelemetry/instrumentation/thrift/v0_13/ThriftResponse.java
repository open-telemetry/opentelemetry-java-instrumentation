/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.thrift.v0_13;

import javax.annotation.Nullable;

public final class ThriftResponse {
  public static final ThriftResponse FAILED = new ThriftResponse(true, null);

  private final boolean failed;
  @Nullable private final String rpcStatusCode;

  private ThriftResponse(boolean failed, @Nullable String rpcStatusCode) {
    this.failed = failed;
    this.rpcStatusCode = rpcStatusCode;
  }

  /** Returns a failed response with its observed RPC status code, if available. */
  public static ThriftResponse failedWithRpcStatusCode(@Nullable String rpcStatusCode) {
    return rpcStatusCode == null ? FAILED : new ThriftResponse(true, rpcStatusCode);
  }

  public boolean isFailed() {
    return failed;
  }

  /** Returns the observed RPC status code, or {@code null} if unavailable. */
  @Nullable
  public String getRpcStatusCode() {
    return rpcStatusCode;
  }
}
