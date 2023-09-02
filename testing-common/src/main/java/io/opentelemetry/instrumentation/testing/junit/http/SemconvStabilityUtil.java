/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.junit.http;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.api.instrumenter.http.internal.HttpAttributes;
import io.opentelemetry.instrumentation.api.instrumenter.network.internal.NetworkAttributes;
import io.opentelemetry.instrumentation.api.instrumenter.url.internal.UrlAttributes;
import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import io.opentelemetry.semconv.SemanticAttributes;
import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("deprecation") // until old http semconv are dropped in 2.0
public class SemconvStabilityUtil {
  private static final Map<AttributeKey<?>, AttributeKey<?>> oldToNewMap = new HashMap<>();

  static {
    addKey(
        oldToNewMap, SemanticAttributes.NET_PROTOCOL_NAME, NetworkAttributes.NETWORK_PROTOCOL_NAME);
    addKey(
        oldToNewMap,
        SemanticAttributes.NET_PROTOCOL_VERSION,
        NetworkAttributes.NETWORK_PROTOCOL_VERSION);
    addKey(oldToNewMap, SemanticAttributes.NET_PEER_NAME, NetworkAttributes.SERVER_ADDRESS);
    addKey(oldToNewMap, SemanticAttributes.NET_PEER_PORT, NetworkAttributes.SERVER_PORT);
    addKey(
        oldToNewMap,
        SemanticAttributes.NET_SOCK_PEER_ADDR,
        NetworkAttributes.CLIENT_SOCKET_ADDRESS);
    addKey(
        oldToNewMap, SemanticAttributes.NET_SOCK_PEER_PORT, NetworkAttributes.CLIENT_SOCKET_PORT);
    addKey(oldToNewMap, SemanticAttributes.HTTP_URL, UrlAttributes.URL_FULL);
    addKey(oldToNewMap, SemanticAttributes.HTTP_METHOD, HttpAttributes.HTTP_REQUEST_METHOD);
    addKey(
        oldToNewMap,
        SemanticAttributes.HTTP_REQUEST_CONTENT_LENGTH,
        HttpAttributes.HTTP_REQUEST_BODY_SIZE);
    addKey(
        oldToNewMap,
        SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH,
        HttpAttributes.HTTP_RESPONSE_BODY_SIZE);
    addKey(
        oldToNewMap, SemanticAttributes.HTTP_STATUS_CODE, HttpAttributes.HTTP_RESPONSE_STATUS_CODE);
    addKey(oldToNewMap, SemanticAttributes.NET_HOST_NAME, NetworkAttributes.SERVER_ADDRESS);
    addKey(oldToNewMap, SemanticAttributes.NET_HOST_PORT, NetworkAttributes.SERVER_PORT);
    addKey(oldToNewMap, SemanticAttributes.HTTP_CLIENT_IP, NetworkAttributes.CLIENT_ADDRESS);
    addKey(oldToNewMap, SemanticAttributes.HTTP_SCHEME, UrlAttributes.URL_SCHEME);
    addKey(
        oldToNewMap,
        SemanticAttributes.NET_SOCK_HOST_ADDR,
        NetworkAttributes.SERVER_SOCKET_ADDRESS);
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
