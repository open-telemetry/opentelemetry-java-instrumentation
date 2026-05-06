/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.thrift.v0_13;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
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
