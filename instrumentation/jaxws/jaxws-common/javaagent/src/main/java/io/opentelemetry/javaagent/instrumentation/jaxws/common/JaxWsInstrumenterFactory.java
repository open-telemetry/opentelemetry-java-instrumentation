/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxws.common;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.bootstrap.internal.ExperimentalConfig;

public final class JaxWsInstrumenterFactory {

  public static Instrumenter<JaxWsRequest, Void> createInstrumenter(String instrumentationName) {
    JaxWsCodeAttributesGetter codeAttributesGetter = new JaxWsCodeAttributesGetter();

    return Instrumenter.<JaxWsRequest, Void>builder(
            GlobalOpenTelemetry.get(),
            instrumentationName,
            CodeSpanNameExtractor.create(codeAttributesGetter))
        .addAttributesExtractor(CodeAttributesExtractor.create(codeAttributesGetter))
        .setEnabled(ExperimentalConfig.get().controllerTelemetryEnabled())
        .buildInstrumenter();
  }

  private JaxWsInstrumenterFactory() {}
}
