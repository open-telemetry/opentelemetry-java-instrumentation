/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kotlinxcoroutines.instrumentationannotations;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.semconv.util.SpanNames;

public final class AnnotationSingletons {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.kotlinx-coroutines-1.0";

  private static final Instrumenter<MethodRequest, Object> INSTRUMENTER = createInstrumenter();

  public static Instrumenter<MethodRequest, Object> instrumenter() {
    return INSTRUMENTER;
  }

  private static Instrumenter<MethodRequest, Object> createInstrumenter() {
    return Instrumenter.builder(
            GlobalOpenTelemetry.get(),
            INSTRUMENTATION_NAME,
            AnnotationSingletons::spanNameFromMethodRequest)
        .addAttributesExtractor(
            CodeAttributesExtractor.create(MethodRequestCodeAttributesGetter.INSTANCE))
        .buildInstrumenter(MethodRequest::getSpanKind);
  }

  private static String spanNameFromMethodRequest(MethodRequest request) {
    String spanName = request.getWithSpanValue();
    if (spanName == null || spanName.isEmpty()) {
      spanName = SpanNames.fromMethod(request.getDeclaringClass(), request.getMethodName());
    }
    return spanName;
  }

  private AnnotationSingletons() {}
}
