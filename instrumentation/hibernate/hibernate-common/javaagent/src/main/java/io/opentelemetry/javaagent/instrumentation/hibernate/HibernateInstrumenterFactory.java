/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hibernate;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.javaagent.bootstrap.internal.AgentInstrumentationConfig;

public final class HibernateInstrumenterFactory {
  static final boolean CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES =
      AgentInstrumentationConfig.get()
          .getBoolean("otel.instrumentation.hibernate.experimental-span-attributes", false);

  public static Instrumenter<HibernateOperation, Void> createInstrumenter(
      String instrumentationName) {
    InstrumenterBuilder<HibernateOperation, Void> instrumenterBuilder =
        Instrumenter.builder(
            GlobalOpenTelemetry.get(), instrumentationName, HibernateOperation::getName);

    if (CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES) {
      instrumenterBuilder.addAttributesExtractor(new HibernateExperimentalAttributesExtractor());
    }

    return instrumenterBuilder.buildInstrumenter();
  }

  private HibernateInstrumenterFactory() {}
}
