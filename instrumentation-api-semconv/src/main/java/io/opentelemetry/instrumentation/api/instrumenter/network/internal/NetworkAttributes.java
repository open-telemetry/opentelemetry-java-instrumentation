/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.network.internal;

import static io.opentelemetry.api.common.AttributeKey.longKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;

import io.opentelemetry.api.common.AttributeKey;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class NetworkAttributes {

  public static final AttributeKey<String> NETWORK_LOCAL_ADDRESS =
      stringKey("network.local.address");

  public static final AttributeKey<Long> NETWORK_LOCAL_PORT = longKey("network.local.port");

  public static final AttributeKey<String> NETWORK_PEER_ADDRESS = stringKey("network.peer.address");

  public static final AttributeKey<Long> NETWORK_PEER_PORT = longKey("network.peer.port");

  private NetworkAttributes() {}
}
