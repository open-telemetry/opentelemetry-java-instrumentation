/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rediscala.v1_8;

public class ServerEndpoint {
  private final String host;
  private final int port;

  public ServerEndpoint(String host, int port) {
    this.host = host;
    this.port = port;
  }

  String getHost() {
    return host;
  }

  int getPort() {
    return port;
  }
}
