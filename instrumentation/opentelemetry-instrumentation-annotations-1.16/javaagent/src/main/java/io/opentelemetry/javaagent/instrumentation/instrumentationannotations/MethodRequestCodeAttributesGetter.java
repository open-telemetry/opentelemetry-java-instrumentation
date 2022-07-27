/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.instrumentationannotations;

import io.opentelemetry.instrumentation.api.instrumenter.code.CodeAttributesGetter;

enum MethodRequestCodeAttributesGetter implements CodeAttributesGetter<MethodRequest> {
  INSTANCE;

  @Override
  public Class<?> codeClass(MethodRequest methodRequest) {
    return methodRequest.method().getDeclaringClass();
  }

  @Override
  public String methodName(MethodRequest methodRequest) {
    return methodRequest.method().getName();
  }
}
