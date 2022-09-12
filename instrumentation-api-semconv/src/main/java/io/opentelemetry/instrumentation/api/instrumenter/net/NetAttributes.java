/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.net;

import io.opentelemetry.api.common.AttributeKey;

// this class will be removed once SemanticAttributes contains all new net.* attributes
final class NetAttributes {

  static final AttributeKey<String> NET_SOCK_FAMILY = AttributeKey.stringKey("net.sock.family");
  static final AttributeKey<String> NET_SOCK_PEER_ADDR =
      AttributeKey.stringKey("net.sock.peer.addr");
  static final AttributeKey<String> NET_SOCK_PEER_NAME =
      AttributeKey.stringKey("net.sock.peer.name");
  static final AttributeKey<Long> NET_SOCK_PEER_PORT = AttributeKey.longKey("net.sock.peer.port");
  static final AttributeKey<String> NET_SOCK_HOST_ADDR =
      AttributeKey.stringKey("net.sock.host.addr");
  static final AttributeKey<Long> NET_SOCK_HOST_PORT = AttributeKey.longKey("net.sock.host.port");

  static final String SOCK_FAMILY_INET = "inet";

  private NetAttributes() {}
}
