/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap;

import io.opentelemetry.instrumentation.api.internal.cache.Cache;

/** Stores the consumed-message metric claim across instrumentation and thread boundaries. */
public final class MessagingMetricCarrier {

  private static final Cache<Object, Boolean> consumedMessages = Cache.weak();

  public static boolean hasConsumedMessages(Object carrier) {
    return carrier != null && Boolean.TRUE.equals(consumedMessages.get(carrier));
  }

  public static void markConsumedMessages(Object carrier) {
    if (carrier != null) {
      consumedMessages.put(carrier, Boolean.TRUE);
    }
  }

  public static void copyConsumedMessages(Object source, Object target) {
    if (target == null) {
      return;
    }
    if (hasConsumedMessages(source)) {
      markConsumedMessages(target);
    } else {
      consumedMessages.remove(target);
    }
  }

  private MessagingMetricCarrier() {}
}
