/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.extannotations;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeAttributeGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeSpanNameExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.util.ClassAndMethod;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;

public final class ExternalAnnotationSingletons {

  private static final Instrumenter<ClassAndMethod, Void> INSTRUMENTER;

  static {
    CodeAttributeGetter<ClassAndMethod> codeAttributeGetter = ClassAndMethod.codeAttributeGetter();

    INSTRUMENTER =
        Instrumenter.<ClassAndMethod, Void>builder(
                GlobalOpenTelemetry.get(),
                "io.opentelemetry.external-annotations",
                CodeSpanNameExtractor.create(codeAttributeGetter))
            .addAttributesExtractor(CodeAttributesExtractor.create(codeAttributeGetter))
            .buildInstrumenter();
  }

  public static Instrumenter<ClassAndMethod, Void> instrumenter() {
    return INSTRUMENTER;
  }

  private ExternalAnnotationSingletons() {}
}
