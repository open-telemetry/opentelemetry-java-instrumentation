/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.instrumentationannotations;

import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeAttributesGetter;

final class MethodRequestCodeAttributesGetter implements CodeAttributesGetter<MethodRequest> {

  @Override
  public Class<?> getCodeClass(MethodRequest methodRequest) {
    return methodRequest.method().getDeclaringClass();
  }

  @Override
  public String getMethodName(MethodRequest methodRequest) {
    return methodRequest.method().getName();
  }
}
