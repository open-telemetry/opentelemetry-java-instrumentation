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

  // FIXME: remove this class and replace its usages with SemanticAttributes once schema 1.20 is
  // released

  public static final AttributeKey<String> NETWORK_TRANSPORT = stringKey("network.transport");

  public static final AttributeKey<String> NETWORK_TYPE = stringKey("network.type");

  public static final AttributeKey<String> NETWORK_PROTOCOL_NAME =
      stringKey("network.protocol.name");

  public static final AttributeKey<String> NETWORK_PROTOCOL_VERSION =
      stringKey("network.protocol.version");

  public static final AttributeKey<String> SERVER_ADDRESS = stringKey("server.address");

  public static final AttributeKey<Long> SERVER_PORT = longKey("server.port");

  public static final AttributeKey<String> SERVER_SOCKET_DOMAIN = stringKey("server.socket.domain");

  public static final AttributeKey<String> SERVER_SOCKET_ADDRESS =
      stringKey("server.socket.address");

  public static final AttributeKey<Long> SERVER_SOCKET_PORT = longKey("server.socket.port");

  public static final AttributeKey<String> CLIENT_ADDRESS = stringKey("client.address");

  public static final AttributeKey<Long> CLIENT_PORT = longKey("client.port");

  public static final AttributeKey<String> CLIENT_SOCKET_ADDRESS =
      stringKey("client.socket.address");

  public static final AttributeKey<Long> CLIENT_SOCKET_PORT = longKey("client.socket.port");

  private NetworkAttributes() {}
}
