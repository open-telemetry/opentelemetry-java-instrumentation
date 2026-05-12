/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vaadin.v14_2;

import javax.annotation.Nullable;

public class VaadinServiceContext {
  private boolean requestHandled;
  @Nullable private String spanNameCandidate;

  void setRequestHandled() {
    requestHandled = true;
  }

  boolean isRequestHandled() {
    return requestHandled;
  }

  void setSpanNameCandidate(String spanNameCandidate) {
    this.spanNameCandidate = spanNameCandidate;
  }

  @Nullable
  String getSpanNameCandidate() {
    return spanNameCandidate;
  }
}
