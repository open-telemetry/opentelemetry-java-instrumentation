/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxws.common;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;

public final class JaxWsInstrumenterFactory {

  public static Instrumenter<JaxWsRequest, Void> createInstrumenter(String instrumentationName) {
    JaxWsCodeAttributesGetter codeAttributesGetter = new JaxWsCodeAttributesGetter();

    return Instrumenter.<JaxWsRequest, Void>builder(
            GlobalOpenTelemetry.get(),
            instrumentationName,
            CodeSpanNameExtractor.create(codeAttributesGetter))
        .addAttributesExtractor(CodeAttributesExtractor.create(codeAttributesGetter))
        .setEnabled(
            DeclarativeConfigUtil.getBoolean(
                    GlobalOpenTelemetry.get(),
                    "java",
                    "common",
                    "controller_telemetry/development",
                    "enabled")
                .orElse(false))
        .buildInstrumenter();
  }

  private JaxWsInstrumenterFactory() {}
}
