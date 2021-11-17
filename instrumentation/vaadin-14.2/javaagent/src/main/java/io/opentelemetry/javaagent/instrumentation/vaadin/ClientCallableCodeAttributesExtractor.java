/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vaadin;

import io.opentelemetry.instrumentation.api.instrumenter.code.CodeAttributesExtractor;
import javax.annotation.Nullable;

public class ClientCallableCodeAttributesExtractor
    extends CodeAttributesExtractor<VaadinClientCallableRequest, Void> {

  @Override
  protected Class<?> codeClass(VaadinClientCallableRequest request) {
    return request.getComponentClass();
  }

  @Override
  protected String methodName(VaadinClientCallableRequest request) {
    return request.getMethodName();
  }

  @Override
  @Nullable
  protected String filePath(VaadinClientCallableRequest request) {
    return null;
  }

  @Override
  @Nullable
  protected Long lineNumber(VaadinClientCallableRequest request) {
    return null;
  }
}
