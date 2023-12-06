/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.extensionannotations;

import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeAttributeGetter;

enum MethodRequestCodeAttributeGetter implements CodeAttributeGetter<MethodRequest> {
  INSTANCE;

  @Override
  public Class<?> getCodeClass(MethodRequest methodRequest) {
    return methodRequest.method().getDeclaringClass();
  }

  @Override
  public String getMethodName(MethodRequest methodRequest) {
    return methodRequest.method().getName();
  }
}
