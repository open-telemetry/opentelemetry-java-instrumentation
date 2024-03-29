/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.semconv.network.internal;

import io.opentelemetry.api.common.AttributeKey;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class NetworkAttributes {

  public static final AttributeKey<String> NETWORK_LOCAL_ADDRESS =
      io.opentelemetry.semconv.NetworkAttributes.NETWORK_LOCAL_ADDRESS;

  public static final AttributeKey<Long> NETWORK_LOCAL_PORT =
      io.opentelemetry.semconv.NetworkAttributes.NETWORK_LOCAL_PORT;

  public static final AttributeKey<String> NETWORK_PEER_ADDRESS =
      io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;

  public static final AttributeKey<Long> NETWORK_PEER_PORT =
      io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;

  private NetworkAttributes() {}
}
