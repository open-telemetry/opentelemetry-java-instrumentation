/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.tapestry;

public final class TapestryRequest {
  private final String eventType;
  private final String componentId;

  public TapestryRequest(String eventType, String componentId) {
    this.eventType = eventType;
    this.componentId = componentId;
  }

  String spanName() {
    return eventType + "/" + componentId;
  }
}
