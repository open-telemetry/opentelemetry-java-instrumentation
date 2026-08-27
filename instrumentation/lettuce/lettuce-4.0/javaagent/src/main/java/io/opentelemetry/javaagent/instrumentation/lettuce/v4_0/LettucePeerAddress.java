/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v4_0;

import java.net.InetSocketAddress;

/**
 * Holds the address of the socket a connection is actually using. A {@code VirtualField} is
 * identified by its owner type and its field type, so the peer needs a type of its own to sit
 * alongside the {@code InetSocketAddress} fields that carry the configured server address.
 */
class LettucePeerAddress {
  private final InetSocketAddress address;

  LettucePeerAddress(InetSocketAddress address) {
    this.address = address;
  }

  InetSocketAddress getAddress() {
    return address;
  }
}
