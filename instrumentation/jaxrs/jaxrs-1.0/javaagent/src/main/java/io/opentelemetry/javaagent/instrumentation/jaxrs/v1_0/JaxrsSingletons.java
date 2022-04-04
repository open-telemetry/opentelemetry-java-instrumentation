/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs.v1_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.config.ExperimentalConfig;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.code.CodeAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.code.CodeSpanNameExtractor;

public final class JaxrsSingletons {

  public static final String ABORT_FILTER_CLASS =
      "io.opentelemetry.javaagent.instrumentation.jaxrs2.filter.abort.class";
  public static final String ABORT_HANDLED =
      "io.opentelemetry.javaagent.instrumentation.jaxrs2.filter.abort.handled";

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
            .setDisabled(ExperimentalConfig.get().suppressControllerSpans())
            .newInstrumenter();
  }

  public static Instrumenter<HandlerData, Void> instrumenter() {
    return INSTRUMENTER;
  }

  private JaxrsSingletons() {}
}
