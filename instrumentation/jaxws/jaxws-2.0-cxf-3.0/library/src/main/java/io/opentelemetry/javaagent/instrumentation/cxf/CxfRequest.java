/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cxf;

import org.apache.cxf.message.Message;

public class CxfRequest {
  private final Message message;
  private final String spanName;

  public CxfRequest(Message message) {
    this.message = message;
    this.spanName = CxfHelper.getSpanName(message);
  }

  public boolean shouldCreateSpan() {
    return spanName != null;
  }

  public String spanName() {
    return spanName;
  }

  public Message message() {
    return message;
  }
}
