/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.sampler.internal;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.sdk.trace.samplers.SamplingDecision;
import java.util.List;
import org.junit.jupiter.api.Test;

class LinksBasedSamplerTest {

  private static final String TRACE_ID = "00000000000000000000000000000001";
  private static final String SPAN_ID = "0000000000000001";

  @Test
  void delegatesToRootSamplerWhenThereAreNoLinks() {
    Sampler sampler = new LinksBasedSampler(Sampler.alwaysOff());

    assertThat(shouldSample(sampler, emptyList())).isEqualTo(SamplingDecision.DROP);
  }

  @Test
  void samplesWhenAnyLinkIsSampled() {
    Sampler sampler = new LinksBasedSampler(Sampler.alwaysOff());

    assertThat(
            shouldSample(
                sampler, asList(link(TraceFlags.getDefault()), link(TraceFlags.getSampled()))))
        .isEqualTo(SamplingDecision.RECORD_AND_SAMPLE);
  }

  @Test
  void dropsWhenAllLinksAreUnsampled() {
    Sampler sampler = new LinksBasedSampler(Sampler.alwaysOn());

    assertThat(
            shouldSample(
                sampler, asList(link(TraceFlags.getDefault()), link(TraceFlags.getDefault()))))
        .isEqualTo(SamplingDecision.DROP);
  }

  private static SamplingDecision shouldSample(Sampler sampler, List<LinkData> links) {
    return sampler
        .shouldSample(
            Context.root(), TRACE_ID, "span", SpanKind.INTERNAL, Attributes.empty(), links)
        .getDecision();
  }

  private static LinkData link(TraceFlags traceFlags) {
    return LinkData.create(
        SpanContext.create(TRACE_ID, SPAN_ID, traceFlags, TraceState.getDefault()));
  }
}
