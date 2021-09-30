/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxws.common;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.code.CodeAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.code.CodeSpanNameExtractor;
import io.opentelemetry.javaagent.instrumentation.api.config.ExperimentalConfig;

public class JaxWsSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.jaxws-common";

  private static final Instrumenter<JaxWsRequest, Void> INSTRUMENTER;
  private static final SpanNameExtractor<JaxWsRequest> SPAN_NAME_EXTRACTOR;

  static {
    CodeAttributesExtractor<JaxWsRequest, Void> codeAttributes = new JaxWsCodeAttributesExtractor();
    SPAN_NAME_EXTRACTOR = CodeSpanNameExtractor.create(codeAttributes);
    INSTRUMENTER =
        Instrumenter.<JaxWsRequest, Void>newBuilder(
                GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, JaxWsRequest::spanName)
            .addAttributesExtractor(codeAttributes)
            .setDisabled(ExperimentalConfig.get().suppressControllerSpans())
            .newInstrumenter();
  }

  public static Instrumenter<JaxWsRequest, Void> instrumenter() {
    return INSTRUMENTER;
  }

  public static SpanNameExtractor<JaxWsRequest> spanNameExtractor() {
    return SPAN_NAME_EXTRACTOR;
  }

  private JaxWsSingletons() {}
}
