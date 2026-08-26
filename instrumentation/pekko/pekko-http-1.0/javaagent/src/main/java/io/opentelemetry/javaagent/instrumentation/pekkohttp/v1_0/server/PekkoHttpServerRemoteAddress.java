/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.server;

import java.net.InetSocketAddress;
import org.apache.pekko.stream.Attributes;

public class PekkoHttpServerRemoteAddress implements Attributes.Attribute {
  private final InetSocketAddress address;

  public PekkoHttpServerRemoteAddress(InetSocketAddress address) {
    this.address = address;
  }

  InetSocketAddress getAddress() {
    return address;
  }
}
