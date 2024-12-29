/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs.v1_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.bootstrap.internal.ExperimentalConfig;

public final class JaxrsSingletons {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.jaxrs-1.0";

  private static final Instrumenter<HandlerData, Void> INSTRUMENTER;

  static {
    JaxrsCodeAttributesGetter codeAttributesGetter = new JaxrsCodeAttributesGetter();

    INSTRUMENTER =
        Instrumenter.<HandlerData, Void>builder(
                GlobalOpenTelemetry.get(),
                INSTRUMENTATION_NAME,
                CodeSpanNameExtractor.create(codeAttributesGetter))
            .addAttributesExtractor(CodeAttributesExtractor.create(codeAttributesGetter))
            .setEnabled(ExperimentalConfig.get().controllerTelemetryEnabled())
            .buildInstrumenter();
  }

  public static Instrumenter<HandlerData, Void> instrumenter() {
    return INSTRUMENTER;
  }

  private JaxrsSingletons() {}
}
