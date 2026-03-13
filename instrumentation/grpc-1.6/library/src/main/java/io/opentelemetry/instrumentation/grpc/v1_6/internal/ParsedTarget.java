/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6.internal;

import javax.annotation.Nullable;

/**
 * Holds the parsed server address and port from a gRPC target string.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class ParsedTarget {

  private final String address;
  @Nullable private final Integer port;

  ParsedTarget(String address, @Nullable Integer port) {
    this.address = address;
    this.port = port;
  }

  public String getAddress() {
    return address;
  }

  @Nullable
  public Integer getPort() {
    return port;
  }
}
