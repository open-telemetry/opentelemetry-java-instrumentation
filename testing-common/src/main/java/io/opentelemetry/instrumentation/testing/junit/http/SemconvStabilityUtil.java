/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.junit.http;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.api.instrumenter.network.internal.NetworkAttributes;
import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import io.opentelemetry.semconv.SemanticAttributes;
import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("deprecation") // until old http semconv are dropped in 2.0
public class SemconvStabilityUtil {
  private static final Map<AttributeKey<?>, AttributeKey<?>> oldToNewMap = new HashMap<>();

  static {
    addKey(
        oldToNewMap,
        SemanticAttributes.NET_PROTOCOL_NAME,
        SemanticAttributes.NETWORK_PROTOCOL_NAME);
    addKey(
        oldToNewMap,
        SemanticAttributes.NET_PROTOCOL_VERSION,
        SemanticAttributes.NETWORK_PROTOCOL_VERSION);
    addKey(oldToNewMap, SemanticAttributes.NET_PEER_NAME, SemanticAttributes.SERVER_ADDRESS);
    addKey(oldToNewMap, SemanticAttributes.NET_PEER_PORT, SemanticAttributes.SERVER_PORT);
    addKey(
        oldToNewMap, SemanticAttributes.NET_SOCK_PEER_ADDR, NetworkAttributes.NETWORK_PEER_ADDRESS);
    addKey(oldToNewMap, SemanticAttributes.NET_SOCK_PEER_PORT, NetworkAttributes.NETWORK_PEER_PORT);
    addKey(oldToNewMap, SemanticAttributes.HTTP_URL, SemanticAttributes.URL_FULL);
    addKey(oldToNewMap, SemanticAttributes.HTTP_METHOD, SemanticAttributes.HTTP_REQUEST_METHOD);
    addKey(
        oldToNewMap,
        SemanticAttributes.HTTP_STATUS_CODE,
        SemanticAttributes.HTTP_RESPONSE_STATUS_CODE);
    addKey(oldToNewMap, SemanticAttributes.NET_HOST_NAME, SemanticAttributes.SERVER_ADDRESS);
    addKey(oldToNewMap, SemanticAttributes.NET_HOST_PORT, SemanticAttributes.SERVER_PORT);
    addKey(oldToNewMap, SemanticAttributes.HTTP_CLIENT_IP, SemanticAttributes.CLIENT_ADDRESS);
    addKey(oldToNewMap, SemanticAttributes.HTTP_SCHEME, SemanticAttributes.URL_SCHEME);
  }

  private SemconvStabilityUtil() {}

  private static <T> void addKey(
      Map<AttributeKey<?>, AttributeKey<?>> map, AttributeKey<T> oldKey, AttributeKey<T> newKey) {
    map.put(oldKey, newKey);
  }

  @SuppressWarnings("unchecked")
  public static <T> AttributeKey<T> getAttributeKey(AttributeKey<T> oldKey) {
    if (SemconvStability.emitStableHttpSemconv()) {
      return (AttributeKey<T>) oldToNewMap.get(oldKey);
    }
    return oldKey;
  }
}
