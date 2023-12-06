/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.util;

import com.google.auto.value.AutoValue;
import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeAttributeGetter;

@AutoValue
public abstract class ClassAndMethod {

  public static CodeAttributeGetter<ClassAndMethod> codeAttributeGetter() {
    return ClassAndMethodAttributeGetter.INSTANCE;
  }

  public static ClassAndMethod create(Class<?> declaringClass, String methodName) {
    return new AutoValue_ClassAndMethod(declaringClass, methodName);
  }

  public abstract Class<?> declaringClass();

  public abstract String methodName();
}
