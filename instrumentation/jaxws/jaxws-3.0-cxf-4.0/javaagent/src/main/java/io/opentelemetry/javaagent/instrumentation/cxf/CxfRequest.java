/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cxf;

import java.util.Objects;
import javax.annotation.Nullable;
import org.apache.cxf.message.Exchange;
import org.apache.cxf.message.Message;
import org.apache.cxf.service.model.BindingOperationInfo;

public class CxfRequest {
  private final Message message;
  private final String spanName;

  public CxfRequest(Message message) {
    this.message = message;
    this.spanName = getSpanName(message);
  }

  public boolean shouldCreateSpan() {
    return spanName != null;
  }

  public String spanName() {
    return Objects.requireNonNull(spanName);
  }

  public Message message() {
    return message;
  }

  @Nullable
  private static String getSpanName(Message message) {
    Exchange exchange = message.getExchange();
    BindingOperationInfo bindingOperationInfo = exchange.get(BindingOperationInfo.class);
    if (bindingOperationInfo == null) {
      return null;
    }

    String serviceName = bindingOperationInfo.getBinding().getService().getName().getLocalPart();
    String operationName = bindingOperationInfo.getOperationInfo().getName().getLocalPart();
    return serviceName + "/" + operationName;
  }
}
