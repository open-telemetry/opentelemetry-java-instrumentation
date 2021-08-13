/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.metro;

import com.sun.xml.ws.api.message.Packet;
import com.sun.xml.ws.api.server.WSEndpoint;

public class MetroRequest {
  private final Packet packet;
  private final String spanName;

  public MetroRequest(WSEndpoint endpoint, Packet packet) {
    this.packet = packet;
    this.spanName = MetroHelper.getSpanName(endpoint, packet);
  }

  public String spanName() {
    return spanName;
  }

  public Packet packet() {
    return packet;
  }
}
