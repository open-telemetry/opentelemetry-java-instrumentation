/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hibernate;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;

public final class HibernateInstrumenterFactory {
  static final boolean CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES =
      DeclarativeConfigUtil.getBoolean(
              GlobalOpenTelemetry.get(), "hibernate", "span_attributes/development")
          .orElse(false);

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
