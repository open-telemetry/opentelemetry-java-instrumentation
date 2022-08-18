/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import static io.opentelemetry.javaagent.tooling.HeliosConfiguration.HELIOS_TEST_TRIGGERED_TRACE;
import static java.util.logging.Level.WARNING;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.sdk.trace.samplers.SamplingResult;
import java.util.List;
import java.util.logging.Logger;

public class HeliosSampler implements Sampler {

  private static final Logger logger = Logger.getLogger(HeliosSampler.class.getName());

  private final Sampler ratioBasedSampler;

  public HeliosSampler(Double ratio) {
    this.ratioBasedSampler = Sampler.traceIdRatioBased(ratio);
  }

  @Override
  public SamplingResult shouldSample(
      Context context,
      String traceId,
      String name,
      SpanKind spanKind,
      Attributes attributes,
      List<LinkData> list) {
    try {
      Baggage baggage = Baggage.fromContext(context);
      if (baggage.getEntryValue(HELIOS_TEST_TRIGGERED_TRACE) != null) {
        return SamplingResult.recordAndSample();
      }
      Span currentSpan = Span.fromContext(context);
      if (currentSpan != null
          && currentSpan.getSpanContext() != null
          && currentSpan.getSpanContext().isSampled()) {
        return SamplingResult.recordAndSample();
      }
    } catch (Exception e) {
      logger.log(WARNING, "Got exception when trying to sample span: " + e);
    }

    return this.ratioBasedSampler.shouldSample(context, traceId, name, spanKind, attributes, list);
  }

  @Override
  public String getDescription() {
    return HeliosSampler.class.getName();
  }
}
