/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.util;

import java.lang.reflect.Method;

/**
 * This class has been stabilized and moved to {@link
 * io.opentelemetry.instrumentation.api.semconv.util.SpanNames}
 */
@Deprecated
public final class SpanNames {

  /**
   * This method is used to generate a span name based on a method. Anonymous classes are named
   * based on their parent.
   */
  public static String fromMethod(Method method) {
    return io.opentelemetry.instrumentation.api.semconv.util.SpanNames.fromMethod(method);
  }

  /**
   * This method is used to generate a span name based on a method. Anonymous classes are named
   * based on their parent.
   */
  public static String fromMethod(Class<?> clazz, String methodName) {
    return io.opentelemetry.instrumentation.api.semconv.util.SpanNames.fromMethod(
        clazz, methodName);
  }

  private SpanNames() {}
}
