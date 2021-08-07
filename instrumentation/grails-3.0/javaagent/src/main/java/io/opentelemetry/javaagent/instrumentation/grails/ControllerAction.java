/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.grails;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class ControllerAction {

  public static ControllerAction create(Object controller, String action) {
    return new AutoValue_ControllerAction(controller, action);
  }

  public abstract Object getController();

  public abstract String getAction();
}
