/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vaadin;

import com.google.auto.value.AutoValue;
import io.opentelemetry.instrumentation.api.util.SpanNames;

@AutoValue
public abstract class VaadinHandlerRequest {

  public static VaadinHandlerRequest create(Class<?> handlerClass, String methodName) {
    return new AutoValue_VaadinHandlerRequest(handlerClass, methodName);
  }

  abstract Class<?> getHandlerClass();

  abstract String getMethodName();

  String getSpanName() {
    return SpanNames.fromMethod(getHandlerClass(), getMethodName());
  }
}
