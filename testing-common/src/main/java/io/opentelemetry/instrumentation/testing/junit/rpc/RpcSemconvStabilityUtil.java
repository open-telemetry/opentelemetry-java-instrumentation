/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.junit.rpc;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
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
  private static final AttributeKey<String> RPC_METHOD = AttributeKey.stringKey("rpc.method");

  private static Map<AttributeKey<?>, AttributeKey<?>> buildMap() {
    Map<AttributeKey<?>, AttributeKey<?>> map = new HashMap<>();
    map.put(RPC_SYSTEM_NAME, RPC_SYSTEM);
    // Note: RPC_METHOD and RPC_SERVICE don't map 1:1 due to format change
    return map;
  }

  private RpcSemconvStabilityUtil() {}

  /**
   * Returns RPC method attribute assertions that work for both old and stable semconv. Pass both
   * service and method parameters, and the helper will return the appropriate assertions based on
   * the semconv mode.
   *
   * <p>Note: In dup mode, old spans use rpc.method.deprecated to avoid collision with stable
   * rpc.method.
   *
   * @param service The RPC service name (e.g., "my.Service")
   * @param method The RPC method name (e.g., "Method")
   * @return List of attribute assertions for the method
   */
  public static List<AttributeAssertion> rpcMethodAssertions(String service, String method) {
    List<AttributeAssertion> assertions = new ArrayList<>();

    if (SemconvStability.emitStableRpcSemconv()) {
      assertions.add(equalTo(RPC_METHOD, service + "/" + method));
    }

    if (SemconvStability.emitOldRpcSemconv()) {
      // Old: rpc.service = "my.Service", rpc.method.deprecated = "Method" (in dup mode)
      //      or rpc.method = "Method" (in old-only mode)
      assertions.add(equalTo(RPC_SERVICE, service));
      assertions.add(equalTo(SemconvStability.getOldRpcMethodAttributeKey(), method));
    }

    return assertions;
  }

  /**
   * Returns RPC system attribute assertion that works for both old and stable semconv.
   *
   * @param systemName The RPC system name (e.g., "grpc", "apache_dubbo")
   * @return Attribute assertion for the system
   */
  public static AttributeAssertion rpcSystemAssertion(String systemName) {
    if (SemconvStability.emitStableRpcSemconv()) {
      return equalTo(RPC_SYSTEM_NAME, SemconvStability.stableRpcSystemName(systemName));
    }
    return equalTo(RPC_SYSTEM, systemName);
  }

  /**
   * Returns the server duration metric name based on semconv mode.
   *
   * @return "rpc.server.call.duration" for stable, "rpc.server.duration" for old
   */
  public static String getServerDurationMetricName() {
    return SemconvStability.emitStableRpcSemconv()
        ? "rpc.server.call.duration"
        : "rpc.server.duration";
  }

  /**
   * Returns the client duration metric name based on semconv mode.
   *
   * @return "rpc.client.call.duration" for stable, "rpc.client.duration" for old
   */
  public static String getClientDurationMetricName() {
    return SemconvStability.emitStableRpcSemconv()
        ? "rpc.client.call.duration"
        : "rpc.client.duration";
  }

  /**
   * Returns the duration unit based on semconv mode.
   *
   * @return "s" (seconds) for stable, "ms" (milliseconds) for old
   */
  public static String getDurationUnit() {
    return SemconvStability.emitStableRpcSemconv() ? "s" : "ms";
  }
}
