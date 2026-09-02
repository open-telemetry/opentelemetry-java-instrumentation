/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.jms;

import io.opentelemetry.instrumentation.api.internal.cache.Cache;

public final class JmsReceiveTelemetry {

  private static final Cache<Object, Boolean> receiveTelemetryRecorded = Cache.weak();

  public static boolean wasRecorded(Object message) {
    return Boolean.TRUE.equals(receiveTelemetryRecorded.get(message));
  }

  public static void markRecorded(Object message) {
    receiveTelemetryRecorded.put(message, Boolean.TRUE);
  }

  public static void copy(Object source, Object target) {
    if (source != null && wasRecorded(source)) {
      markRecorded(target);
    } else {
      receiveTelemetryRecorded.remove(target);
    }
  }

  private JmsReceiveTelemetry() {}
}
