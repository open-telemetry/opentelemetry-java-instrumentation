/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs.v2_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.config.ExperimentalConfig;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.code.CodeAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.code.CodeSpanNameExtractor;

final class JaxrsInstrumenterFactory {

  public static Instrumenter<HandlerData, Void> createInstrumenter(String instrumentationName) {
    JaxrsCodeAttributesGetter codeAttributesGetter = new JaxrsCodeAttributesGetter();

    return Instrumenter.<HandlerData, Void>builder(
            GlobalOpenTelemetry.get(),
            instrumentationName,
            CodeSpanNameExtractor.create(codeAttributesGetter))
        .addAttributesExtractor(CodeAttributesExtractor.create(codeAttributesGetter))
        .setEnabled(ExperimentalConfig.get().controllerTelemetryEnabled())
        .newInstrumenter();
  }

  private JaxrsInstrumenterFactory() {}
}
