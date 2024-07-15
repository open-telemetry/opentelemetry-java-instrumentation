/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.annotations;

import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeAttributesGetter;

enum JointPointCodeAttributesExtractor implements CodeAttributesGetter<JoinPointRequest> {
  INSTANCE;

  @Override
  public Class<?> getCodeClass(JoinPointRequest joinPointRequest) {
    return joinPointRequest.method().getDeclaringClass();
  }

  @Override
  public String getMethodName(JoinPointRequest joinPointRequest) {
    return joinPointRequest.method().getName();
  }
}
