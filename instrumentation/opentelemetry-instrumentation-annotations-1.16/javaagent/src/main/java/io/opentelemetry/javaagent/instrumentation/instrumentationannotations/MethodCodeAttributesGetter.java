/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.instrumentationannotations;

import io.opentelemetry.instrumentation.api.instrumenter.code.CodeAttributesGetter;
import java.lang.reflect.Method;

enum MethodCodeAttributesGetter implements CodeAttributesGetter<Method> {
  INSTANCE;

  @Override
  public Class<?> codeClass(Method method) {
    return method.getDeclaringClass();
  }

  @Override
  public String methodName(Method method) {
    return method.getName();
  }
}
