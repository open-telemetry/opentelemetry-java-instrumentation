/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.event;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.runner.JobRunner;
import io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions;
import io.opentelemetry.sdk.testing.assertj.TraceAssert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public abstract class CustomSpanEventTest {
  private final JobRunner runner;

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  public CustomSpanEventTest(JobRunner runner) {
    this.runner = runner;
  }

  @Test
  public void should_be_able_to_call_Span_current___and_add_custom_info_to_spans() {
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
                                    OpenTelemetryAssertions.assertThat(spanData)
                                        .hasEventsSatisfyingExactly(
                                            event -> event.hasName("step.before"),
                                            event -> event.hasName("step.after"));
                                  } else {
                                    OpenTelemetryAssertions.assertThat(spanData)
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
                                    OpenTelemetryAssertions.assertThat(spanData)
                                        .hasEventsSatisfyingExactly(
                                            event -> event.hasName("chunk.before"),
                                            event -> event.hasName("chunk.after"));
                                  } else {
                                    assertThat(spanData.getEvents()).isEmpty();
                                  }
                                }))));
  }

  protected void itemSpans(TraceAssert trace) {
    trace.hasSpansSatisfyingExactly(
        span -> {}, // already checked in the outer method
        span -> {}, // already checked in the outer method
        span -> {}, // already checked in the outer method
        span ->
            span.hasName("BatchJob customSpanEventsItemsJob.customSpanEventsItemStep.ItemRead")
                .hasKind(SpanKind.INTERNAL)
                .hasParent(trace.getSpan(2))
                .hasEventsSatisfyingExactly(
                    event -> event.hasName("item.read.before"),
                    event -> event.hasName("item.read.after")),
        span ->
            span.hasName("BatchJob customSpanEventsItemsJob.customSpanEventsItemStep.ItemRead")
                .hasKind(SpanKind.INTERNAL)
                .hasParent(trace.getSpan(2))
                // spring batch does not call ItemReadListener after() methods when read() returns
                // end-of-stream
                .hasEventsSatisfyingExactly(event -> event.hasName("item.read.before")),
        span ->
            span.hasName("BatchJob customSpanEventsItemsJob.customSpanEventsItemStep.ItemProcess")
                .hasKind(SpanKind.INTERNAL)
                .hasParent(trace.getSpan(2))
                .hasEventsSatisfyingExactly(
                    event -> event.hasName("item.process.before"),
                    event -> event.hasName("item.process.after")),
        span ->
            span.hasName("BatchJob customSpanEventsItemsJob.customSpanEventsItemStep.ItemWrite")
                .hasKind(SpanKind.INTERNAL)
                .hasParent(trace.getSpan(2))
                .hasEventsSatisfyingExactly(
                    event -> event.hasName("item.write.before"),
                    event -> event.hasName("item.write.after")));
  }

  private static final boolean VERSION_GREATER_THAN_4_0 = Boolean.getBoolean("testLatestDeps");
}