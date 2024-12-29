/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.methods;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeSpanNameExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.util.ClassAndMethod;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;

public final class MethodSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.methods";

  private static final Instrumenter<ClassAndMethod, Void> INSTRUMENTER;
  private static final ClassLoader bootstrapLoader = new BootstrapLoader();

  static {
    CodeAttributesGetter<ClassAndMethod> codeAttributesGetter =
        ClassAndMethod.codeAttributesGetter();

    INSTRUMENTER =
        Instrumenter.<ClassAndMethod, Void>builder(
                GlobalOpenTelemetry.get(),
                INSTRUMENTATION_NAME,
                CodeSpanNameExtractor.create(codeAttributesGetter))
            .addAttributesExtractor(CodeAttributesExtractor.create(codeAttributesGetter))
            .buildInstrumenter(SpanKindExtractor.alwaysInternal());
  }

  public static Instrumenter<ClassAndMethod, Void> instrumenter() {
    return INSTRUMENTER;
  }

  public static ClassLoader getBootstrapLoader() {
    return bootstrapLoader;
  }

  private MethodSingletons() {}

  private static class BootstrapLoader extends ClassLoader {
    static {
      ClassLoader.registerAsParallelCapable();
    }

    BootstrapLoader() {
      super(null);
    }
  }
}
