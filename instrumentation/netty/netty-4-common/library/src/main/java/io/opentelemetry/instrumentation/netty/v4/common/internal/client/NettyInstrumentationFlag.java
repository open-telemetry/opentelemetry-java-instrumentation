/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.netty.v4.common.internal.client;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public enum NettyInstrumentationFlag {
  ENABLED,
  ERROR_ONLY,
  DISABLED;

  public static NettyInstrumentationFlag enabledOrErrorOnly(boolean b) {
    return b ? ENABLED : ERROR_ONLY;
  }
}
