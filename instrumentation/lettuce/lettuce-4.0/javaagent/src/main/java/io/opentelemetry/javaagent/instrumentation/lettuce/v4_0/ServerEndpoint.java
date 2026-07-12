/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v4_0;

public class ServerEndpoint {
  private final String address;
  private final int port;

  public ServerEndpoint(String address, int port) {
    this.address = address;
    this.port = port;
  }

  String getAddress() {
    return address;
  }

  int getPort() {
    return port;
  }
}
