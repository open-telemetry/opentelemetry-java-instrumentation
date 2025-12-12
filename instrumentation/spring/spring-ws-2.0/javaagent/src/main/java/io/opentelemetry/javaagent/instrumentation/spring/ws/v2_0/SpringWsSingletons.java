/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.ws.v2_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;

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

  public static Instrumenter<SpringWsRequest, Void> instrumenter() {
    return INSTRUMENTER;
  }

  private SpringWsSingletons() {}
}
