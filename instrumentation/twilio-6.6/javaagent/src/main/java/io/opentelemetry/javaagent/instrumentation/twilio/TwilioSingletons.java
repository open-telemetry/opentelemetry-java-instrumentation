/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.twilio;

import static io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor.alwaysClient;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNames;

public class TwilioSingletons {

  private static final boolean CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES =
      Config.get().getBoolean("otel.instrumentation.twilio.experimental-span-attributes", false);

  private static final Instrumenter<String, Object> INSTRUMENTER;

  static {
    InstrumenterBuilder<String, Object> instrumenterBuilder =
        Instrumenter.<String, Object>builder(
            GlobalOpenTelemetry.get(), "io.opentelemetry.twilio-6.6", str -> str);

    if (CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES) {
      instrumenterBuilder.addAttributesExtractor(new TwilioExperimentalAttributesExtractor());
    }

    INSTRUMENTER = instrumenterBuilder.newInstrumenter(alwaysClient());
  }

  public static Instrumenter<String, Object> instrumenter() {
    return INSTRUMENTER;
  }

  /** Derive span name from service execution metadata. */
  public static String spanName(Object serviceExecutor, String methodName) {
    return SpanNames.fromMethod(serviceExecutor.getClass(), methodName);
  }
}
