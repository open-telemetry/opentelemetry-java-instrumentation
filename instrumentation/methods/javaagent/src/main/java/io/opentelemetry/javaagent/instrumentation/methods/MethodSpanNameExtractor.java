/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.methods;

import static io.opentelemetry.instrumentation.api.tracer.BaseTracer.spanNameForMethod;

import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import java.lang.reflect.Method;

public final class MethodSpanNameExtractor implements SpanNameExtractor<Method> {
  @Override
  public String extract(Method method) {
    return spanNameForMethod(method);
  }
}
