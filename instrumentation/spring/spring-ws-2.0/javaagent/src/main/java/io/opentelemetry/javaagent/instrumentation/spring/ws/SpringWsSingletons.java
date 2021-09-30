/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.ws;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.code.CodeAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.code.CodeSpanNameExtractor;
import io.opentelemetry.javaagent.instrumentation.api.config.ExperimentalConfig;

public class SpringWsSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.spring-ws-2.0";

  private static final Instrumenter<SpringWsRequest, Void> INSTRUMENTER;

  static {
    CodeAttributesExtractor<SpringWsRequest, Void> codeAttributes =
        new SpringWsCodeAttributesExtractor();
    INSTRUMENTER =
        Instrumenter.<SpringWsRequest, Void>newBuilder(
                GlobalOpenTelemetry.get(),
                INSTRUMENTATION_NAME,
                CodeSpanNameExtractor.create(codeAttributes))
            .addAttributesExtractor(codeAttributes)
            .setDisabled(ExperimentalConfig.get().suppressControllerSpans())
            .newInstrumenter();
  }

  public static Instrumenter<SpringWsRequest, Void> instrumenter() {
    return INSTRUMENTER;
  }

  private SpringWsSingletons() {}
}
