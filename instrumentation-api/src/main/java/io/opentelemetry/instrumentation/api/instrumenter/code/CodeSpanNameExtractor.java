/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.code;

import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.tracer.ClassNames;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * A helper {@link SpanNameExtractor} implementation for instrumentations that target specific Java
 * classes/methods.
 */
public final class CodeSpanNameExtractor<REQUEST> implements SpanNameExtractor<REQUEST> {

  /**
   * Returns a {@link SpanNameExtractor} that constructs the span name according to the following
   * pattern: {@code <class.simpleName>.<methodName>}.
   */
  public static <REQUEST> SpanNameExtractor<REQUEST> create(
      CodeAttributesExtractor<REQUEST, ?> attributesExtractor) {
    return new CodeSpanNameExtractor<>(attributesExtractor);
  }

  private final CodeAttributesExtractor<REQUEST, ?> attributesExtractor;

  private CodeSpanNameExtractor(CodeAttributesExtractor<REQUEST, ?> attributesExtractor) {
    this.attributesExtractor = attributesExtractor;
  }

  @Override
  public String extract(REQUEST request) {
    Class<?> cls = attributesExtractor.codeClass(request);
    String className = cls != null ? ClassNames.simpleName(cls) : "<unknown>";
    String methodName = defaultString(attributesExtractor.methodName(request));
    return className + "." + methodName;
  }

  private static String defaultString(@Nullable String s) {
    return s == null ? "<unknown>" : s;
  }
}
