/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.code;

import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.internal.ClassNames;

/**
 * A helper {@link SpanNameExtractor} implementation for instrumentations that target specific Java
 * classes/methods.
 */
public final class CodeSpanNameExtractor<REQUEST> implements SpanNameExtractor<REQUEST> {

  /**
   * Returns a {@link SpanNameExtractor} that constructs the span name according to the following
   * pattern: {@code <class.simpleName>.<methodName>}.
   */
  public static <REQUEST> SpanNameExtractor<REQUEST> create(CodeAttributesGetter<REQUEST> getter) {
    return new CodeSpanNameExtractor<>(getter);
  }

  private final CodeAttributesGetter<REQUEST> getter;

  private CodeSpanNameExtractor(CodeAttributesGetter<REQUEST> getter) {
    this.getter = getter;
  }

  @Override
  public String extract(REQUEST request) {
    Class<?> cls = getter.getCodeClass(request);
    String className = cls != null ? ClassNames.simpleName(cls) : "<unknown>";
    int lambdaIdx = className.indexOf("$$Lambda");
    if (lambdaIdx > -1) {
      // need to produce low-cardinality name, since lambda class names change with each restart
      className = className.substring(0, lambdaIdx + "$$Lambda".length());
    }
    String methodName = getter.getMethodName(request);
    if (methodName == null) {
      return className;
    }
    return className + "." + methodName;
  }
}
