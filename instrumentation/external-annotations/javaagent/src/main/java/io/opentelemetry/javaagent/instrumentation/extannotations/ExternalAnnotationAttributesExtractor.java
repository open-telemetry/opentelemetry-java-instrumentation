/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.extannotations;

import io.opentelemetry.instrumentation.api.instrumenter.code.CodeAttributesExtractor;
import javax.annotation.Nullable;

final class ExternalAnnotationAttributesExtractor
    extends CodeAttributesExtractor<ClassAndMethod, Void> {

  @Override
  protected Class<?> codeClass(ClassAndMethod classAndMethod) {
    return classAndMethod.declaringClass();
  }

  @Override
  protected String methodName(ClassAndMethod classAndMethod) {
    return classAndMethod.methodName();
  }

  @Nullable
  @Override
  protected String filePath(ClassAndMethod classAndMethod) {
    return null;
  }

  @Nullable
  @Override
  protected Long lineNumber(ClassAndMethod classAndMethod) {
    return null;
  }
}
