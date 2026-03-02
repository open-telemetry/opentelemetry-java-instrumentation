/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.junit.rpc;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableRpcSemconv;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SYSTEM;

import io.opentelemetry.api.common.AttributeKey;
import java.util.HashMap;
import java.util.Map;

// until old rpc semconv are dropped in 3.0
@SuppressWarnings("deprecation") // using deprecated semconv
public final class SemconvRpcStabilityUtil {

  private static final AttributeKey<String> RPC_SYSTEM_NAME =
      AttributeKey.stringKey("rpc.system.name");

  private static final Map<AttributeKey<?>, AttributeKey<?>> oldToNewMap = buildMap();

  private static Map<AttributeKey<?>, AttributeKey<?>> buildMap() {
    Map<AttributeKey<?>, AttributeKey<?>> map = new HashMap<>();
    map.put(RPC_SYSTEM, RPC_SYSTEM_NAME);
    return map;
  }

  private SemconvRpcStabilityUtil() {}

  @SuppressWarnings("unchecked")
  public static <T> AttributeKey<T> maybeStable(AttributeKey<T> oldKey) {
    // not testing rpc/dup
    if (emitStableRpcSemconv()) {
      return (AttributeKey<T>) oldToNewMap.get(oldKey);
    }
    return oldKey;
  }
}
