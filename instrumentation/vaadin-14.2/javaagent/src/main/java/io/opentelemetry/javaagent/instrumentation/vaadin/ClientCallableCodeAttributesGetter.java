/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vaadin;

import io.opentelemetry.instrumentation.api.instrumenter.code.CodeAttributesGetter;

public class ClientCallableCodeAttributesGetter
    implements CodeAttributesGetter<VaadinClientCallableRequest> {

  @Override
  public Class<?> codeClass(VaadinClientCallableRequest request) {
    return request.getComponentClass();
  }

  @Override
  public String methodName(VaadinClientCallableRequest request) {
    return request.getMethodName();
  }
}
