/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxws.v2_0.axis2.v1_6;

import org.apache.axis2.jaxws.core.MessageContext;

class Axis2Request {
  private final MessageContext message;
  private final String spanName;

  Axis2Request(MessageContext message) {
    this.message = message;
    this.spanName = getSpanName(message);
  }

  MessageContext message() {
    return message;
  }

  String spanName() {
    return spanName;
  }

  private static String getSpanName(MessageContext message) {
    org.apache.axis2.context.MessageContext axisMessageContext = message.getAxisMessageContext();
    String serviceName = axisMessageContext.getOperationContext().getServiceName();
    String operationName = axisMessageContext.getOperationContext().getOperationName();
    return serviceName + "/" + operationName;
  }
}
