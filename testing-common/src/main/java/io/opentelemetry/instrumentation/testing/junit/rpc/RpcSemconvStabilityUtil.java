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
import java.util.Objects;
import org.assertj.core.data.MapEntry;

// until old rpc semconv are dropped in 3.0
public class RpcSemconvStabilityUtil {

  // Stable semconv keys - need to reference from the extractors since they're not public in semconv
  private static final AttributeKey<String> RPC_SYSTEM_NAME =
      AttributeKey.stringKey("rpc.system.name");
  private static final AttributeKey<String> RPC_METHOD_STABLE =
      AttributeKey.stringKey("rpc.method");
  private static final AttributeKey<String> RPC_METHOD_ORIGINAL =
      AttributeKey.stringKey("rpc.method_original");

  private static final Map<AttributeKey<?>, AttributeKey<?>> newToOldMap = buildMap();

  @SuppressWarnings("deprecation") // using deprecated semconv
  private static Map<AttributeKey<?>, AttributeKey<?>> buildMap() {
    Map<AttributeKey<?>, AttributeKey<?>> map = new HashMap<>();
    map.put(RPC_SYSTEM_NAME, RPC_SYSTEM);
    map.put(RPC_METHOD_ORIGINAL, RPC_METHOD);
    // Note: RPC_METHOD and RPC_SERVICE don't map 1:1 due to format change
    return map;
  }

  private RpcSemconvStabilityUtil() {}

  // not testing rpc/dup
  @SuppressWarnings("unchecked")
  public static <T> AttributeKey<T> maybeUnstable(AttributeKey<T> key) {
    if (SemconvStability.emitOldRpcSemconv()) {
      return (AttributeKey<T>) Objects.requireNonNull(newToOldMap.get(key));
    }
    return key;
  }

  // not testing rpc/dup
  public static AttributeAssertion maybeUnstableMethod(String serviceName, String methodName) {
    if (SemconvStability.emitOldRpcSemconv()) {
      return equalTo(RPC_SERVICE, serviceName);
    } else {
      return equalTo(RPC_METHOD_STABLE, serviceName + "/" + methodName);
    }
  }

  // not testing rpc/dup
  public static MapEntry<AttributeKey<String>, String> maybeUnstableMethodEntry(
      String serviceName, String methodName) {
    if (SemconvStability.emitOldRpcSemconv()) {
      return MapEntry.entry(RPC_SERVICE, serviceName);
    } else {
      return MapEntry.entry(RPC_METHOD_STABLE, serviceName + "/" + methodName);
    }
  }

  /**
   * Returns RPC method attribute assertions that work for both old and stable semconv. Pass both
   * service and method parameters, and the helper will return the appropriate assertions based on
   * the semconv mode.
   *
   * <p>For metric assertions, use {@link #rpcMetricMethodAssertions(String, String)} instead, which
   * excludes rpc.method_original.
   *
   * <p>Note: In dup mode, stable rpc.method takes precedence over old rpc.method due to key name
   * collision. Both use "rpc.method" but with different formats (fully-qualified vs method name
   * only).
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
      // In dup mode, skip old rpc.method since stable version takes precedence
      if (!SemconvStability.emitStableRpcSemconv()) {
        assertions.add(equalTo(RPC_METHOD, method));
      }
    }

    return assertions;
  }

  /**
   * Returns RPC method attribute assertions for metrics that work for both old and stable semconv.
   * This excludes rpc.method_original which is a span attribute but not a metric dimension.
   *
   * <p>Note: In dup mode, stable rpc.method takes precedence over old rpc.method due to key name
   * collision.
   *
   * @param service The RPC service name (e.g., "my.Service")
   * @param method The RPC method name (e.g., "Method")
   * @return List of attribute assertions for the method in metrics
   */
  @SuppressWarnings("deprecation") // testing deprecated rpc semconv
  public static List<AttributeAssertion> rpcMetricMethodAssertions(String service, String method) {
    List<AttributeAssertion> assertions = new ArrayList<>();

    if (SemconvStability.emitStableRpcSemconv()) {
      // Stable: rpc.method = "my.Service/Method"
      // Note: rpc.method_original is NOT a metric dimension
      assertions.add(equalTo(RPC_METHOD_STABLE, service + "/" + method));
    }

    if (SemconvStability.emitOldRpcSemconv()) {
      // Old: rpc.service = "my.Service", rpc.method = "Method"
      assertions.add(equalTo(RPC_SERVICE, service));
      // In dup mode, skip old rpc.method since stable version takes precedence
      if (!SemconvStability.emitStableRpcSemconv()) {
        assertions.add(equalTo(RPC_METHOD, method));
      }
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
