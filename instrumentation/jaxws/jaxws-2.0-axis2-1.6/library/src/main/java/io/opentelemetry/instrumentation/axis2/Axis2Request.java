/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.axis2;

import org.apache.axis2.jaxws.core.MessageContext;

public class Axis2Request {
  private final MessageContext message;
  private final String spanName;

  public Axis2Request(MessageContext message) {
    this.message = message;
    this.spanName = Axis2Helper.getSpanName(message);
  }

  public MessageContext message() {
    return message;
  }

  public String spanName() {
    return spanName;
  }
}
