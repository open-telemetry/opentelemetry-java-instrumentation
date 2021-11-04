/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vaadin;

import com.google.auto.value.AutoValue;
import io.opentelemetry.instrumentation.api.tracer.SpanNames;

@AutoValue
public abstract class VaadinServiceRequest {

  public static VaadinServiceRequest create(Class<?> serviceClass, String methodName) {
    return new AutoValue_VaadinServiceRequest(serviceClass, methodName);
  }

  abstract Class<?> getServiceClass();

  abstract String getMethodName();

  String getSpanName() {
    return SpanNames.fromMethod(getServiceClass(), getMethodName());
  }
}
