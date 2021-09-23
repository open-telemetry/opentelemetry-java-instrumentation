/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.grails;

import io.opentelemetry.instrumentation.api.tracer.SpanNames;

public class HandlerData {

  private final Object controller;
  private final String action;

  public HandlerData(Object controller, String action) {
    this.controller = controller;
    this.action = action;
  }

  String spanName() {
    return SpanNames.fromMethod(controller.getClass(), action);
  }
}
