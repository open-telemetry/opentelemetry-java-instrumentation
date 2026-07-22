/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.junit.message;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;

import io.opentelemetry.api.common.AttributeKey;
import java.util.HashMap;
import java.util.Map;

// supports asserting on old messaging semconv, to be removed in 3.0
public class SemconvMessagingStabilityUtil {

  private static final Map<AttributeKey<?>, AttributeKey<?>> oldToNewMap = buildMap();

  private static Map<AttributeKey<?>, AttributeKey<?>> buildMap() {
    Map<AttributeKey<?>, AttributeKey<?>> map = new HashMap<>();
    map.put(stringKey("messaging.client_id"), stringKey("messaging.client.id"));
    return map;
  }

  @SuppressWarnings("unchecked")
  public static <T> AttributeKey<T> effectiveKey(AttributeKey<T> oldKey) {
    // not testing messaging/dup
    if (emitStableMessagingSemconv()) {
      return (AttributeKey<T>) oldToNewMap.getOrDefault(oldKey, oldKey);
    }
    return oldKey;
  }

  private SemconvMessagingStabilityUtil() {}
}
