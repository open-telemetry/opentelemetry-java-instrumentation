/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.jms;

import io.opentelemetry.instrumentation.api.internal.cache.Cache;

public final class JmsReceiveTelemetry {

  private static final Cache<Object, Boolean> RECEIVE_TELEMETRY_RECORDED = Cache.weak();

  public static boolean wasRecorded(Object message) {
    return Boolean.TRUE.equals(RECEIVE_TELEMETRY_RECORDED.get(message));
  }

  public static void markRecorded(Object message) {
    RECEIVE_TELEMETRY_RECORDED.put(message, Boolean.TRUE);
  }

  public static void copy(Object source, Object target) {
    if (source != null && wasRecorded(source)) {
      markRecorded(target);
    } else {
      RECEIVE_TELEMETRY_RECORDED.remove(target);
    }
  }

  private JmsReceiveTelemetry() {}
}
