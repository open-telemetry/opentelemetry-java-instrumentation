/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.scheduling;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.code.CodeAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.code.CodeSpanNameExtractor;

public final class SpringSchedulingSingletons {

  private static final Instrumenter<Runnable, Void> INSTRUMENTER;

  static {
    SpringSchedulingCodeAttributesGetter codeAttributesGetter =
        new SpringSchedulingCodeAttributesGetter();
    INSTRUMENTER =
        Instrumenter.<Runnable, Void>builder(
                GlobalOpenTelemetry.get(),
                "io.opentelemetry.spring-scheduling-3.1",
                CodeSpanNameExtractor.create(codeAttributesGetter))
            .addAttributesExtractor(CodeAttributesExtractor.create(codeAttributesGetter))
            .newInstrumenter();
  }

  public static Instrumenter<Runnable, Void> instrumenter() {
    return INSTRUMENTER;
  }

  private SpringSchedulingSingletons() {}
}
