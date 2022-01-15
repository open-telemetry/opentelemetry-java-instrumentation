/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.axis2;

import org.apache.axis2.jaxws.core.MessageContext;

public class Axis2Request {
  private final MessageContext message;
  private final String spanName;

  public Axis2Request(MessageContext message) {
    this.message = message;
    this.spanName = getSpanName(message);
  }

  public MessageContext message() {
    return message;
  }

  public String spanName() {
    return spanName;
  }

  private static String getSpanName(MessageContext message) {
    org.apache.axis2.context.MessageContext axisMessageContext = message.getAxisMessageContext();
    String serviceName = axisMessageContext.getOperationContext().getServiceName();
    String operationName = axisMessageContext.getOperationContext().getOperationName();
    return serviceName + "/" + operationName;
  }
}
