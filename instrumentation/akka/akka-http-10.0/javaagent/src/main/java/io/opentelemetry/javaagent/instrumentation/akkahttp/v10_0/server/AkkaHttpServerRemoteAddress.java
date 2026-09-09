/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkahttp.v10_0.server;

import akka.stream.Attributes;
import java.net.InetSocketAddress;

class AkkaHttpServerRemoteAddress implements Attributes.Attribute {
  private final InetSocketAddress remoteAddress;

  AkkaHttpServerRemoteAddress(InetSocketAddress remoteAddress) {
    this.remoteAddress = remoteAddress;
  }

  InetSocketAddress remoteAddress() {
    return remoteAddress;
  }
}
