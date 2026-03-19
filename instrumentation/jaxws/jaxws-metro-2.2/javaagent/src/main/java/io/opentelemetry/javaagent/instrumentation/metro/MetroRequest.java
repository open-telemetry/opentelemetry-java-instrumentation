/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.metro;

import com.sun.xml.ws.api.message.Packet;
import com.sun.xml.ws.api.server.WSEndpoint;
import javax.annotation.Nullable;
import javax.xml.namespace.QName;

public class MetroRequest {
  private final Packet packet;
  @Nullable private final String spanName;

  public MetroRequest(WSEndpoint<?> endpoint, Packet packet) {
    this.packet = packet;
    this.spanName = getSpanName(endpoint, packet);
  }

  @Nullable
  public String spanName() {
    return spanName;
  }

  public Packet packet() {
    return packet;
  }

  @Nullable
  private static String getSpanName(WSEndpoint<?> endpoint, Packet packet) {
    QName wsdlOperation = packet.getWSDLOperation();
    if (wsdlOperation == null) {
      return null;
    }
    String serviceName = endpoint.getServiceName().getLocalPart();
    String operationName = wsdlOperation.getLocalPart();
    return serviceName + "/" + operationName;
  }
}
