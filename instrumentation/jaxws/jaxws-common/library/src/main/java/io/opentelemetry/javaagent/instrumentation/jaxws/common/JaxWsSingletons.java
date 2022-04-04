/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxws.common;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.config.ExperimentalConfig;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.code.CodeAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.code.CodeSpanNameExtractor;

public class JaxWsSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.jaxws-common";

  private static final Instrumenter<JaxWsRequest, Void> INSTRUMENTER;

  static {
    JaxWsCodeAttributesGetter codeAttributesGetter = new JaxWsCodeAttributesGetter();

    INSTRUMENTER =
        Instrumenter.<JaxWsRequest, Void>builder(
                GlobalOpenTelemetry.get(),
                INSTRUMENTATION_NAME,
                CodeSpanNameExtractor.create(codeAttributesGetter))
            .addAttributesExtractor(CodeAttributesExtractor.create(codeAttributesGetter))
            .setEnabled(ExperimentalConfig.get().controllerTelemetryEnabled())
            .newInstrumenter();
  }

  public static Instrumenter<JaxWsRequest, Void> instrumenter() {
    return INSTRUMENTER;
  }

  private JaxWsSingletons() {}
}
