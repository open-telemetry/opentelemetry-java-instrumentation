/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.bootstrap.internal.ExperimentalConfig;

public final class JaxrsInstrumenterFactory {

  public static Instrumenter<HandlerData, Void> createInstrumenter(String instrumentationName) {
    JaxrsCodeAttributeGetter codeAttributeGetter = new JaxrsCodeAttributeGetter();

    return Instrumenter.<HandlerData, Void>builder(
            GlobalOpenTelemetry.get(),
            instrumentationName,
            CodeSpanNameExtractor.create(codeAttributeGetter))
        .addAttributesExtractor(CodeAttributesExtractor.create(codeAttributeGetter))
        .setEnabled(ExperimentalConfig.get().controllerTelemetryEnabled())
        .buildInstrumenter();
  }

  private JaxrsInstrumenterFactory() {}
}
