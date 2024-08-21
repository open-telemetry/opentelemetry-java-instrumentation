/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.event;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.runner.JobRunner;
import io.opentelemetry.sdk.testing.assertj.TraceAssert;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

abstract class CustomSpanEventTest {
  private final JobRunner runner;

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  public CustomSpanEventTest(JobRunner runner) {
    this.runner = runner;
  }

  @Test
  void shouldBeAbleToCallSpanCurrentAndAddCustomInfoToSpans() {
    runner.runJob("customSpanEventsItemsJob");

    testing.waitAndAssertTraces(
        trace ->
            itemSpans(
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("BatchJob customSpanEventsItemsJob")
                            .hasKind(SpanKind.INTERNAL)
                            .hasEventsSatisfyingExactly(
                                event -> event.hasName("job.before"),
                                event -> event.hasName("job.after")),
                    span ->
                        span.hasName("BatchJob customSpanEventsItemsJob.customSpanEventsItemStep")
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(0))
                            .satisfies(
                                spanData -> {
                                  // CompositeChunkListener has broken ordering that causes
                                  // listeners that do not override order() to appear first at all
                                  // times
                                  // because of that a custom ChunkListener will always see a Step
                                  // span when using spring-batch versions [3, 4)
                                  // that bug was fixed in 4.0
                                  if (VERSION_GREATER_THAN_4_0) {
                                    assertThat(spanData)
                                        .hasEventsSatisfyingExactly(
                                            event -> event.hasName("step.before"),
                                            event -> event.hasName("step.after"));
                                  } else {
                                    assertThat(spanData)
                                        .hasEventsSatisfyingExactly(
                                            event -> event.hasName("step.before"),
                                            event -> event.hasName("chunk.before"),
                                            event -> event.hasName("chunk.after"),
                                            event -> event.hasName("step.after"));
                                  }
                                }),
                    span ->
                        span.hasName(
                                "BatchJob customSpanEventsItemsJob.customSpanEventsItemStep.Chunk")
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(1))
                            .satisfies(
                                spanData -> {

                                  // CompositeChunkListener has broken ordering that causes
                                  // listeners that do not override order() to appear first at all
                                  // times
                                  // because of that a custom ChunkListener will always see a Step
                                  // span when using spring-batch versions [3, 4)
                                  // that bug was fixed in 4.0
                                  if (VERSION_GREATER_THAN_4_0) {
                                    assertThat(spanData)
                                        .hasEventsSatisfyingExactly(
                                            event -> event.hasName("chunk.before"),
                                            event -> event.hasName("chunk.after"));
                                  } else {
                                    Assertions.assertThat(spanData.getEvents()).isEmpty();
                                  }
                                }),
                    span -> {}, // ignore
                    span -> {}, // ignore
                    span -> {}, // ignore
                    span -> {} // ignore
                    )));
  }

  protected void itemSpans(TraceAssert trace) {
    trace.hasSpansSatisfyingExactly(
        span -> {}, // already checked in the outer method
        span -> {}, // already checked in the outer method
        span -> {}, // already checked in the outer method
        span ->
            span.hasName("BatchJob customSpanEventsItemsJob.customSpanEventsItemStep.ItemRead")
                .hasKind(SpanKind.INTERNAL)
                .hasParent(trace.getSpan(2)),
        span ->
            span.hasName("BatchJob customSpanEventsItemsJob.customSpanEventsItemStep.ItemRead")
                .hasKind(SpanKind.INTERNAL)
                .hasParent(trace.getSpan(2)),
        span ->
            span.hasName("BatchJob customSpanEventsItemsJob.customSpanEventsItemStep.ItemProcess")
                .hasKind(SpanKind.INTERNAL)
                .hasParent(trace.getSpan(2)),
        span ->
            span.hasName("BatchJob customSpanEventsItemsJob.customSpanEventsItemStep.ItemWrite")
                .hasKind(SpanKind.INTERNAL)
                .hasParent(trace.getSpan(2)));
  }

  private static final boolean VERSION_GREATER_THAN_4_0 = Boolean.getBoolean("testLatestDeps");
}
