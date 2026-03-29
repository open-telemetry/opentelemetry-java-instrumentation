/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.metro;

import com.sun.xml.ws.api.message.Packet;
import com.sun.xml.ws.api.server.WSEndpoint;
import javax.xml.namespace.QName;

public class MetroRequest {
  private final Packet packet;
  private final String spanName;

  public MetroRequest(WSEndpoint<?> endpoint, Packet packet) {
    this.packet = packet;
    this.spanName = getSpanName(endpoint, packet);
  }

  public String spanName() {
    return spanName;
  }

  public Packet packet() {
    return packet;
  }

  private static String getSpanName(WSEndpoint<?> endpoint, Packet packet) {
    String serviceName = endpoint.getServiceName().getLocalPart();
    QName wsdlOperation = packet.getWSDLOperation();
    if (wsdlOperation == null) {
      return serviceName;
    }
    return serviceName + "/" + wsdlOperation.getLocalPart();
  }
}
