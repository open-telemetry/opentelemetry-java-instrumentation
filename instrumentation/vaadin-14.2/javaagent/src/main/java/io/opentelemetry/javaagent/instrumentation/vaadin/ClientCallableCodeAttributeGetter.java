/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vaadin;

import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeAttributeGetter;

public class ClientCallableCodeAttributeGetter
    implements CodeAttributeGetter<VaadinClientCallableRequest> {

  @Override
  public Class<?> getCodeClass(VaadinClientCallableRequest request) {
    return request.getComponentClass();
  }

  @Override
  public String getMethodName(VaadinClientCallableRequest request) {
    return request.getMethodName();
  }
}
