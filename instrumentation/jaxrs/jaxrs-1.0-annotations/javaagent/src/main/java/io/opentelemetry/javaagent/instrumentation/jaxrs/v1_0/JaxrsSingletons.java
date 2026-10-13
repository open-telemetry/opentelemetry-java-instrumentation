/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs.v1_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.internal.EmbeddedInstrumentationProperties;
import io.opentelemetry.instrumentation.api.semconv.code.CodeAttributesExtractor;
import io.opentelemetry.instrumentation.api.semconv.code.CodeSpanNameExtractor;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.bootstrap.internal.ExperimentalConfig;

class JaxrsSingletons {

  private static final String VERSION_LOOKUP_NAME = "io.opentelemetry.jaxrs-1.0-annotations";
  private static final String INSTRUMENTATION_NAME =
      AgentCommonConfig.get().isV3Preview() ? VERSION_LOOKUP_NAME : "io.opentelemetry.jaxrs-1.0";

  private static final Instrumenter<HandlerData, Void> instrumenter;

  static {
    JaxrsCodeAttributesGetter codeAttributesGetter = new JaxrsCodeAttributesGetter();

    InstrumenterBuilder<HandlerData, Void> builder =
        Instrumenter.<HandlerData, Void>builder(
                GlobalOpenTelemetry.get(),
                INSTRUMENTATION_NAME,
                CodeSpanNameExtractor.create(codeAttributesGetter))
            .addAttributesExtractor(CodeAttributesExtractor.create(codeAttributesGetter))
            .setEnabled(ExperimentalConfig.get().controllerTelemetryEnabled());
    String version = EmbeddedInstrumentationProperties.findVersion(VERSION_LOOKUP_NAME);
    if (version != null) {
      builder.setInstrumentationVersion(version);
    }
    instrumenter = builder.buildInstrumenter();
  }

  static Instrumenter<HandlerData, Void> instrumenter() {
    return instrumenter;
  }

  private JaxrsSingletons() {}
}
