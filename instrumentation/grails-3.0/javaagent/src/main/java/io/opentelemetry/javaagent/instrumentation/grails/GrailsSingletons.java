/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.grails;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;

public final class GrailsSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.grails-3.0";

  private static final Instrumenter<HandlerData, Void> INSTRUMENTER;

  static {
    GrailsCodeAttributesGetter codeAttributesGetter = new GrailsCodeAttributesGetter();

    INSTRUMENTER =
        Instrumenter.<HandlerData, Void>builder(
                GlobalOpenTelemetry.get(),
                INSTRUMENTATION_NAME,
                CodeSpanNameExtractor.create(codeAttributesGetter))
            .addAttributesExtractor(CodeAttributesExtractor.create(codeAttributesGetter))
            .setEnabled(
                DeclarativeConfigUtil.getBoolean(
                        GlobalOpenTelemetry.get(),
                        "java",
                        "common",
                        "controller_telemetry/development",
                        "enabled")
                    .orElse(false))
            .buildInstrumenter();
  }

  public static Instrumenter<HandlerData, Void> instrumenter() {
    return INSTRUMENTER;
  }

  private GrailsSingletons() {}
}
