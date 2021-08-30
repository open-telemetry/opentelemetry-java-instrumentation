/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.quartz.v2_0;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import java.util.ArrayList;
import java.util.List;
import org.quartz.JobExecutionContext;

/** A builder of {@link QuartzTracing}. */
public final class QuartzTracingBuilder {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.quartz-1.7";

  private final OpenTelemetry openTelemetry;

  private final List<AttributesExtractor<? super JobExecutionContext, ? super Void>>
      additionalExtractors = new ArrayList<>();

  QuartzTracingBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  /**
   * Adds an additional {@link AttributesExtractor} to invoke to set attributes to instrumented
   * items. The {@link AttributesExtractor} will be executed after all default extractors.
   */
  public QuartzTracingBuilder addAttributeExtractor(
      AttributesExtractor<? super JobExecutionContext, ? super Void> attributesExtractor) {
    additionalExtractors.add(attributesExtractor);
    return this;
  }

  /** Returns a new {@link QuartzTracing} with the settings of this {@link QuartzTracingBuilder}. */
  public QuartzTracing build() {
    InstrumenterBuilder<JobExecutionContext, Void> instrumenter =
        Instrumenter.newBuilder(openTelemetry, INSTRUMENTATION_NAME, new QuartzSpanNameExtractor());

    instrumenter.addAttributesExtractors(additionalExtractors);

    return new QuartzTracing(new TracingJobListener(instrumenter.newInstrumenter()));
  }
}
