/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.twilio;

import static io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor.alwaysClient;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.semconv.util.SpanNames;

class TwilioSingletons {

  private static final boolean CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES =
      DeclarativeConfigUtil.getInstrumentationConfig(GlobalOpenTelemetry.get(), "twilio")
          .getBoolean("experimental_span_attributes/development", false);

  private static final Instrumenter<String, Object> instrumenter;

  static {
    InstrumenterBuilder<String, Object> instrumenterBuilder =
        Instrumenter.builder(GlobalOpenTelemetry.get(), "io.opentelemetry.twilio-6.6", str -> str);

    if (CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES) {
      instrumenterBuilder.addAttributesExtractor(new TwilioExperimentalAttributesExtractor());
    }

    instrumenter = instrumenterBuilder.buildInstrumenter(alwaysClient());
  }

  static Instrumenter<String, Object> instrumenter() {
    return instrumenter;
  }

  /** Derive span name from service execution metadata. */
  static String spanName(Object serviceExecutor, String methodName) {
    return SpanNames.fromMethod(serviceExecutor.getClass(), methodName);
  }

  private TwilioSingletons() {}
}
