/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.methods;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.api.incubator.semconv.util.ClassAndMethod;

public class MethodAndType {
  private final ClassAndMethod classAndMethod;
  private final SpanKind spanKind;

  private MethodAndType(ClassAndMethod classAndMethod, SpanKind spanKind) {
    this.classAndMethod = classAndMethod;
    this.spanKind = spanKind;
  }

  public static MethodAndType create(ClassAndMethod classAndMethod, SpanKind spanKind) {
    return new MethodAndType(classAndMethod, spanKind);
  }

  public ClassAndMethod getClassAndMethod() {
    return classAndMethod;
  }

  public SpanKind getSpanKind() {
    return spanKind;
  }
}
