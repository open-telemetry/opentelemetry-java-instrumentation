/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.util;

import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeAttributeGetter;
import javax.annotation.Nullable;

enum ClassAndMethodAttributeGetter implements CodeAttributeGetter<ClassAndMethod> {
  INSTANCE;

  @Nullable
  @Override
  public Class<?> getCodeClass(ClassAndMethod classAndMethod) {
    return classAndMethod.declaringClass();
  }

  @Nullable
  @Override
  public String getMethodName(ClassAndMethod classAndMethod) {
    return classAndMethod.methodName();
  }
}
