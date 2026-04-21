/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.grails;

public class HandlerData {

  private final Object controller;
  private final String action;

  public HandlerData(Object controller, String action) {
    this.controller = controller;
    this.action = action;
  }

  Object getController() {
    return controller;
  }

  String getAction() {
    return action;
  }
}
