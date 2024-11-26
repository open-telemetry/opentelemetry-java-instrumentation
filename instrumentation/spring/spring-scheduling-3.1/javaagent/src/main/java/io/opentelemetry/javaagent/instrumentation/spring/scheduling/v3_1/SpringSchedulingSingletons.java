/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.scheduling.v3_1;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.javaagent.bootstrap.internal.AgentInstrumentationConfig;

public final class SpringSchedulingSingletons {

  private static final boolean CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES =
      AgentInstrumentationConfig.get()
          .getBoolean("otel.instrumentation.spring-scheduling.experimental-span-attributes", false);

  private static final Instrumenter<Runnable, Void> INSTRUMENTER;

  static {
    SpringSchedulingCodeAttributesGetter codeAttributesGetter =
        new SpringSchedulingCodeAttributesGetter();

    InstrumenterBuilder<Runnable, Void> builder =
        Instrumenter.<Runnable, Void>builder(
                GlobalOpenTelemetry.get(),
                "io.opentelemetry.spring-scheduling-3.1",
                CodeSpanNameExtractor.create(codeAttributesGetter))
            .addAttributesExtractor(CodeAttributesExtractor.create(codeAttributesGetter));

    if (CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES) {
      builder.addAttributesExtractor(
          AttributesExtractor.constant(AttributeKey.stringKey("job.system"), "spring_scheduling"));
    }

    INSTRUMENTER = builder.buildInstrumenter();
  }

  public static Instrumenter<Runnable, Void> instrumenter() {
    return INSTRUMENTER;
  }

  private SpringSchedulingSingletons() {}
}
