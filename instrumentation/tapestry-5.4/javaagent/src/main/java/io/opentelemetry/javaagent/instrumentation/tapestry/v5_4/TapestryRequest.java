/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.tapestry.v5_4;

class TapestryRequest {
  private final String eventType;
  private final String componentId;

  TapestryRequest(String eventType, String componentId) {
    this.eventType = eventType;
    this.componentId = componentId;
  }

  String spanName() {
    return eventType + "/" + componentId;
  }
}
