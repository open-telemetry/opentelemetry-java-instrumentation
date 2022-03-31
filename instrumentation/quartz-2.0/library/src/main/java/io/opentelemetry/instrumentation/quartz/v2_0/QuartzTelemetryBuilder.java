/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.quartz.v2_0;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.code.CodeAttributesExtractor;
import java.util.ArrayList;
import java.util.List;
import org.quartz.JobExecutionContext;

/** A builder of {@link QuartzTelemetry}. */
public final class QuartzTelemetryBuilder {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.quartz-2.0";

  private final OpenTelemetry openTelemetry;

  private final List<AttributesExtractor<? super JobExecutionContext, ? super Void>>
      additionalExtractors = new ArrayList<>();

  QuartzTelemetryBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  /**
   * Adds an additional {@link AttributesExtractor} to invoke to set attributes to instrumented
   * items. The {@link AttributesExtractor} will be executed after all default extractors.
   */
  public QuartzTelemetryBuilder addAttributeExtractor(
      AttributesExtractor<? super JobExecutionContext, ? super Void> attributesExtractor) {
    additionalExtractors.add(attributesExtractor);
    return this;
  }

  /**
   * Returns a new {@link QuartzTelemetry} with the settings of this {@link QuartzTelemetryBuilder}.
   */
  public QuartzTelemetry build() {
    InstrumenterBuilder<JobExecutionContext, Void> instrumenter =
        Instrumenter.builder(openTelemetry, INSTRUMENTATION_NAME, new QuartzSpanNameExtractor());

    instrumenter.setErrorCauseExtractor(new QuartzErrorCauseExtractor());
    instrumenter.addAttributesExtractor(
        CodeAttributesExtractor.create(new QuartzCodeAttributesGetter()));
    instrumenter.addAttributesExtractors(additionalExtractors);

    return new QuartzTelemetry(new TracingJobListener(instrumenter.newInstrumenter()));
  }
}
