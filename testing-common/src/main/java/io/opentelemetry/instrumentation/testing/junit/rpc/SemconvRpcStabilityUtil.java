/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.junit.rpc;

import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SYSTEM;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SYSTEM_NAME;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("deprecation") // using deprecated semconv
public class SemconvRpcStabilityUtil {
  private static final Map<AttributeKey<?>, AttributeKey<?>> oldToNewMap = buildMap();

  private static Map<AttributeKey<?>, AttributeKey<?>> buildMap() {
    Map<AttributeKey<?>, AttributeKey<?>> map = new HashMap<>();
    map.put(RPC_SYSTEM, RPC_SYSTEM_NAME);
    return map;
  }

  private SemconvRpcStabilityUtil() {}

  public static boolean emitOldRpcSemconv() {
    return SemconvStability.emitOldRpcSemconv();
  }

  public static boolean emitPreviewRpcSemconv() {
    return SemconvStability.emitPreviewRpcSemconv();
  }

  @SuppressWarnings("unchecked")
  public static <T> AttributeKey<T> maybeStable(AttributeKey<T> oldKey) {
    // not testing rpc/dup
    if (emitPreviewRpcSemconv()) {
      return (AttributeKey<T>) oldToNewMap.get(oldKey);
    }
    return oldKey;
  }
}
