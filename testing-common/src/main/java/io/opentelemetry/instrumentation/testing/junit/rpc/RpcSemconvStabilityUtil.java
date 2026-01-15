/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.junit.rpc;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_METHOD;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SERVICE;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SYSTEM;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// until old rpc semconv are dropped in 3.0
public class RpcSemconvStabilityUtil {

  // Stable semconv keys - need to reference from the extractors since they're not public in semconv
  private static final AttributeKey<String> RPC_SYSTEM_NAME =
      AttributeKey.stringKey("rpc.system.name");
  private static final AttributeKey<String> RPC_METHOD_STABLE =
      AttributeKey.stringKey("rpc.method");
  private static final AttributeKey<String> RPC_METHOD_ORIGINAL =
      AttributeKey.stringKey("rpc.method_original");

  private static final Map<AttributeKey<?>, AttributeKey<?>> oldToNewMap = buildMap();

  @SuppressWarnings("deprecation") // using deprecated semconv
  private static Map<AttributeKey<?>, AttributeKey<?>> buildMap() {
    Map<AttributeKey<?>, AttributeKey<?>> map = new HashMap<>();
    map.put(RPC_SYSTEM, RPC_SYSTEM_NAME);
    // Note: RPC_METHOD and RPC_SERVICE don't map 1:1 due to format change
    return map;
  }

  private RpcSemconvStabilityUtil() {}

  // not testing rpc/dup
  @SuppressWarnings("unchecked")
  public static <T> AttributeKey<T> maybeStable(AttributeKey<T> oldKey) {
    if (SemconvStability.emitStableRpcSemconv()) {
      AttributeKey<?> stableKey = oldToNewMap.get(oldKey);
      if (stableKey != null) {
        return (AttributeKey<T>) stableKey;
      }
    }
    return oldKey;
  }

  public static String maybeStableRpcSystemName(String oldRpcSystemName) {
    // not testing rpc/dup
    if (SemconvStability.emitStableRpcSemconv()) {
      return SemconvStability.stableRpcSystemName(oldRpcSystemName);
    }
    return oldRpcSystemName;
  }

  /**
   * Returns RPC method attribute assertions that work for both old and stable semconv. Pass both
   * service and method parameters, and the helper will return the appropriate assertions based on
   * the semconv mode.
   *
   * @param service The RPC service name (e.g., "my.Service")
   * @param method The RPC method name (e.g., "Method")
   * @return List of attribute assertions for the method
   */
  @SuppressWarnings("deprecation") // testing deprecated rpc semconv
  public static List<AttributeAssertion> rpcMethodAssertions(String service, String method) {
    List<AttributeAssertion> assertions = new ArrayList<>();

    if (SemconvStability.emitStableRpcSemconv()) {
      // Stable: rpc.method = "my.Service/Method", rpc.method_original = "Method"
      assertions.add(equalTo(RPC_METHOD_STABLE, service + "/" + method));
      assertions.add(equalTo(RPC_METHOD_ORIGINAL, method));
    }

    if (SemconvStability.emitOldRpcSemconv()) {
      // Old: rpc.service = "my.Service", rpc.method = "Method"
      assertions.add(equalTo(RPC_SERVICE, service));
      assertions.add(equalTo(RPC_METHOD, method));
    }

    return assertions;
  }

  /**
   * Returns RPC system attribute assertion that works for both old and stable semconv.
   *
   * @param systemName The RPC system name (e.g., "grpc", "apache_dubbo")
   * @return Attribute assertion for the system
   */
  @SuppressWarnings("deprecation") // testing deprecated rpc semconv
  public static AttributeAssertion rpcSystemAssertion(String systemName) {
    if (SemconvStability.emitStableRpcSemconv()) {
      return equalTo(RPC_SYSTEM_NAME, SemconvStability.stableRpcSystemName(systemName));
    }
    return equalTo(RPC_SYSTEM, systemName);
  }
}
