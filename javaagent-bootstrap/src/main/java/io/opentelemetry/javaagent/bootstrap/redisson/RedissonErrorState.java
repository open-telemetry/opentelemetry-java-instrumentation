/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.redisson;

import io.opentelemetry.instrumentation.api.internal.ScopedThreadValue;

public final class RedissonErrorState {
  private static final ScopedThreadValue<String> currentServerErrorType = new ScopedThreadValue<>();

  public static ScopedThreadValue<String> currentServerErrorType() {
    return currentServerErrorType;
  }

  private RedissonErrorState() {}
}
