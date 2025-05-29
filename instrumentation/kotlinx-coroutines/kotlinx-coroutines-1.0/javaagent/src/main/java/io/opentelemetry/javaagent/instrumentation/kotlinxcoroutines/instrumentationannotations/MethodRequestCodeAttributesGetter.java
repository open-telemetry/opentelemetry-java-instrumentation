/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kotlinxcoroutines.instrumentationannotations;

import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeAttributesGetter;

enum MethodRequestCodeAttributesGetter implements CodeAttributesGetter<MethodRequest> {
  INSTANCE;

  @Override
  public Class<?> getCodeClass(MethodRequest methodRequest) {
    return methodRequest.getDeclaringClass();
  }

  @Override
  public String getMethodName(MethodRequest methodRequest) {
    return methodRequest.getMethodName();
  }
}
