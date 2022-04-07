/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.ws;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.config.ExperimentalConfig;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.code.CodeAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.code.CodeSpanNameExtractor;

public class SpringWsSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.spring-ws-2.0";

  private static final Instrumenter<SpringWsRequest, Void> INSTRUMENTER;

  static {
    SpringWsCodeAttributesGetter codeAttributesGetter = new SpringWsCodeAttributesGetter();

    INSTRUMENTER =
        Instrumenter.<SpringWsRequest, Void>builder(
                GlobalOpenTelemetry.get(),
                INSTRUMENTATION_NAME,
                CodeSpanNameExtractor.create(codeAttributesGetter))
            .addAttributesExtractor(CodeAttributesExtractor.create(codeAttributesGetter))
            .setEnabled(ExperimentalConfig.get().controllerTelemetryEnabled())
            .newInstrumenter();
  }

  public static Instrumenter<SpringWsRequest, Void> instrumenter() {
    return INSTRUMENTER;
  }

  private SpringWsSingletons() {}
}
