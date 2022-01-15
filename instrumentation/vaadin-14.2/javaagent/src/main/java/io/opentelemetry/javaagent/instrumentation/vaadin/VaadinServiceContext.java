/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vaadin;

public final class VaadinServiceContext {
  private boolean requestHandled;
  private String spanNameCandidate;

  void setRequestHandled() {
    requestHandled = true;
  }

  boolean isRequestHandled() {
    return requestHandled;
  }

  void setSpanNameCandidate(String spanNameCandidate) {
    this.spanNameCandidate = spanNameCandidate;
  }

  String getSpanNameCandidate() {
    return spanNameCandidate;
  }
}
