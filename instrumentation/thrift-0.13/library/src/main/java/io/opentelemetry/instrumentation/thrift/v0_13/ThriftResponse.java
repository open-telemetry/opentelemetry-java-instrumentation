/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.thrift.v0_13;

public final class ThriftResponse {
  public static final ThriftResponse FAILED = new ThriftResponse(true);

  private final boolean failed;

  private ThriftResponse(boolean failed) {
    this.failed = failed;
  }

  public boolean isFailed() {
    return failed;
  }
}
