/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs.v1_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.code.CodeAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.code.CodeSpanNameExtractor;
import io.opentelemetry.javaagent.instrumentation.api.config.ExperimentalConfig;

public final class JaxrsSingletons {

  public static final String ABORT_FILTER_CLASS =
      "io.opentelemetry.javaagent.instrumentation.jaxrs2.filter.abort.class";
  public static final String ABORT_HANDLED =
      "io.opentelemetry.javaagent.instrumentation.jaxrs2.filter.abort.handled";

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.jaxrs-1.0-common";

  private static final Instrumenter<HandlerData, Void> INSTRUMENTER;

  static {
    CodeAttributesExtractor<HandlerData, Void> codeAttributesExtractor =
        new JaxrsCodeAttributesExtractor();
    SpanNameExtractor<HandlerData> spanNameExtractor =
        CodeSpanNameExtractor.create(codeAttributesExtractor);

    INSTRUMENTER =
        Instrumenter.<HandlerData, Void>newBuilder(
                GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, spanNameExtractor)
            .addAttributesExtractor(codeAttributesExtractor)
            .setDisabled(ExperimentalConfig.get().suppressControllerSpans())
            .newInstrumenter();
  }

  public static Instrumenter<HandlerData, Void> instrumenter() {
    return INSTRUMENTER;
  }

  private JaxrsSingletons() {}
}
