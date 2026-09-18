/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v3_0;

import com.datastax.driver.core.EndPoint;
import com.datastax.driver.core.SniEndPoint;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

class TestEndPoints {

  // SniEndPoint.resolve() looks the proxy host up with InetAddress.getAllByName, so a literal
  // address keeps every test offline and deterministic.
  static final InetSocketAddress PROXY_ADDRESS =
      InetSocketAddress.createUnresolved("127.0.0.1", 29042);

  static SniEndPoint sniEndPoint() {
    return sniEndPoint("host-id");
  }

  static SniEndPoint sniEndPoint(String serverName) {
    return new SniEndPoint(PROXY_ADDRESS, serverName);
  }

  static EndPoint plainEndPoint(InetSocketAddress address) {
    return () -> address;
  }

  // Build addresses from raw bytes so they are resolved without a hostname lookup.
  static InetSocketAddress address(byte[] ip, int port) throws UnknownHostException {
    return new InetSocketAddress(InetAddress.getByAddress(ip), port);
  }

  private TestEndPoints() {}
}
