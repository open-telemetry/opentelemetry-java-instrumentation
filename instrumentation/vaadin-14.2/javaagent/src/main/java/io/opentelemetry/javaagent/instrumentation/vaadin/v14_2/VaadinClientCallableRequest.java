/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vaadin.v14_2;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class VaadinClientCallableRequest {

  public static VaadinClientCallableRequest create(Class<?> componentClass, String methodName) {
    return new AutoValue_VaadinClientCallableRequest(componentClass, methodName);
  }

  abstract Class<?> getComponentClass();

  abstract String getMethodName();
}
