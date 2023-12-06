/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.methods;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeAttributeGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeSpanNameExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.util.ClassAndMethod;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;

public final class MethodSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.methods";

  private static final Instrumenter<ClassAndMethod, Void> INSTRUMENTER;

  static {
    CodeAttributeGetter<ClassAndMethod> codeAttributeGetter = ClassAndMethod.codeAttributeGetter();

    INSTRUMENTER =
        Instrumenter.<ClassAndMethod, Void>builder(
                GlobalOpenTelemetry.get(),
                INSTRUMENTATION_NAME,
                CodeSpanNameExtractor.create(codeAttributeGetter))
            .addAttributesExtractor(CodeAttributesExtractor.create(codeAttributeGetter))
            .buildInstrumenter(SpanKindExtractor.alwaysInternal());
  }

  public static Instrumenter<ClassAndMethod, Void> instrumenter() {
    return INSTRUMENTER;
  }

  private MethodSingletons() {}
}
