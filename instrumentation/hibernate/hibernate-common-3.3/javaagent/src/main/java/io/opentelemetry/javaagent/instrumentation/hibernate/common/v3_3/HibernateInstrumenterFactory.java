/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hibernate.common.v3_3;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.internal.EmbeddedInstrumentationProperties;

public class HibernateInstrumenterFactory {
  static final boolean CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES =
      DeclarativeConfigUtil.getInstrumentationConfig(GlobalOpenTelemetry.get(), "hibernate")
          .getBoolean("experimental_span_attributes/development", false);

  public static Instrumenter<HibernateOperation, Void> createInstrumenter(
      String instrumentationName) {
    return createInstrumenter(instrumentationName, instrumentationName);
  }

  public static Instrumenter<HibernateOperation, Void> createInstrumenter(
      String instrumentationName, String versionLookupName) {
    InstrumenterBuilder<HibernateOperation, Void> instrumenterBuilder =
        Instrumenter.builder(
            GlobalOpenTelemetry.get(), instrumentationName, HibernateOperation::getName);

    String version = EmbeddedInstrumentationProperties.findVersion(versionLookupName);
    if (version != null) {
      instrumenterBuilder.setInstrumentationVersion(version);
    }

    if (CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES) {
      instrumenterBuilder.addAttributesExtractor(new HibernateExperimentalAttributesExtractor());
    }

    return instrumenterBuilder.buildInstrumenter();
  }

  private HibernateInstrumenterFactory() {}
}
