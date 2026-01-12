/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.methods;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import javax.annotation.Nullable;

public final class MethodSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.methods";

  private static final Instrumenter<MethodAndType, Void> INSTRUMENTER;
  private static final ClassLoader bootstrapLoader = new BootstrapLoader();

  static {
    CodeAttributesGetter<MethodAndType> codeAttributesGetter =
        new CodeAttributesGetter<MethodAndType>() {
          @Nullable
          @Override
          public Class<?> getCodeClass(MethodAndType methodAndType) {
            return methodAndType.getClassAndMethod().declaringClass();
          }

          @Nullable
          @Override
          public String getMethodName(MethodAndType methodAndType) {
            return methodAndType.getClassAndMethod().methodName();
          }
        };

    INSTRUMENTER =
        Instrumenter.<MethodAndType, Void>builder(
                GlobalOpenTelemetry.get(),
                INSTRUMENTATION_NAME,
                CodeSpanNameExtractor.create(codeAttributesGetter))
            .addAttributesExtractor(CodeAttributesExtractor.create(codeAttributesGetter))
            .buildInstrumenter(MethodAndType::getSpanKind);
  }

  public static Instrumenter<MethodAndType, Void> instrumenter() {
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
