/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cxf;

import org.apache.cxf.message.Exchange;
import org.apache.cxf.message.Message;
import org.apache.cxf.service.Service;
import org.apache.cxf.service.model.BindingOperationInfo;

public class CxfRequest {
  private final Message message;
  private final String spanName;

  public CxfRequest(Message message) {
    this.message = message;
    this.spanName = getSpanName(message);
  }

  public String spanName() {
    return spanName;
  }

  public Message message() {
    return message;
  }

  private static String getSpanName(Message message) {
    Exchange exchange = message.getExchange();
    BindingOperationInfo bindingOperationInfo = exchange.get(BindingOperationInfo.class);
    if (bindingOperationInfo != null) {
      String serviceName = bindingOperationInfo.getBinding().getService().getName().getLocalPart();
      String operationName = bindingOperationInfo.getOperationInfo().getName().getLocalPart();
      return serviceName + "/" + operationName;
    }
    Service service = exchange.getService();
    if (service != null && service.getName() != null) {
      return service.getName().getLocalPart();
    }
    return "jaxws";
  }
}
