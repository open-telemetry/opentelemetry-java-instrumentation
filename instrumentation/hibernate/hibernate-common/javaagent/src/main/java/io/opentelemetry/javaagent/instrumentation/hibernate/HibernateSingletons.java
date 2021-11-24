/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hibernate;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;

public class HibernateSingletons {

  static final boolean CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES =
      Config.get().getBoolean("otel.instrumentation.hibernate.experimental-span-attributes", false);

  private static final Instrumenter<HibernateOperation, Void> INSTANCE;

  static {
    InstrumenterBuilder<HibernateOperation, Void> instrumenterBuilder =
        Instrumenter.builder(
            GlobalOpenTelemetry.get(),
            "io.opentelemetry.hibernate-common",
            HibernateOperation::getName);

    if (CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES) {
      instrumenterBuilder.addAttributesExtractor(new HibernateExperimentalAttributesExtractor());
    }

    INSTANCE = instrumenterBuilder.newInstrumenter();
  }

  public static Instrumenter<HibernateOperation, Void> instrumenter() {
    return INSTANCE;
  }
}
