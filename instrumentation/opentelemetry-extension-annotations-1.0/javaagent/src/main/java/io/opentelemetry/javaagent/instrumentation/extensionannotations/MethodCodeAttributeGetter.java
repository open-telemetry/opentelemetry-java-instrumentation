/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.extensionannotations;

import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeAttributeGetter;
import java.lang.reflect.Method;

enum MethodCodeAttributeGetter implements CodeAttributeGetter<Method> {
  INSTANCE;

  @Override
  public Class<?> getCodeClass(Method method) {
    return method.getDeclaringClass();
  }

  @Override
  public String getMethodName(Method method) {
    return method.getName();
  }
}
