/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.basic;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static java.util.Collections.singletonMap;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.runner.JobRunner;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.testing.assertj.TraceAssert;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.batch.core.JobParameter;

abstract class SpringBatchTest {

  private final JobRunner runner;

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  public SpringBatchTest(JobRunner runner) {
    this.runner = runner;
  }

  @Test
  void shouldTraceTaskletJobStep() {
    runner.runJob("taskletJob");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("BatchJob taskletJob")
                        .hasKind(SpanKind.INTERNAL)
                        .hasAttribute(AttributeKey.stringKey("job.system"), "spring_batch"),
                span ->
                    span.hasName("BatchJob taskletJob.step")
                        .hasKind(SpanKind.INTERNAL)
                        .hasTotalAttributeCount(0)
                        .hasParent(trace.getSpan(0)),
                span ->
                    span.hasName("BatchJob taskletJob.step.Tasklet")
                        .hasKind(SpanKind.INTERNAL)
                        .hasTotalAttributeCount(0)
                        .hasParent(trace.getSpan(1))));
  }

  @Test
  void shouldHandleExceptionInTaskletJobStep() {
    runner.runJob("taskletJob", singletonMap("fail", new JobParameter(1L)));

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("BatchJob taskletJob")
                        .hasKind(SpanKind.INTERNAL)
                        .hasAttribute(AttributeKey.stringKey("job.system"), "spring_batch"),
                span ->
                    span.hasName("BatchJob taskletJob.step")
                        .hasKind(SpanKind.INTERNAL)
                        .hasTotalAttributeCount(0)
                        .hasParent(trace.getSpan(0)),
                span ->
                    span.hasName("BatchJob taskletJob.step.Tasklet")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(1))
                        .hasStatus(StatusData.error())
                        .hasTotalAttributeCount(0)
                        .hasException(new IllegalStateException("fail"))));
  }

  @Test
  void shouldTraceChunkedItemsJob() {
    runner.runJob("itemsAndTaskletJob");

    testing.waitAndAssertTraces(
        trace -> {
          Consumer<SpanDataAssert> chunk =
              span ->
                  span.hasName("BatchJob itemsAndTaskletJob.itemStep.Chunk")
                      .hasKind(SpanKind.INTERNAL)
                      .hasTotalAttributeCount(0)
                      .hasParent(trace.getSpan(1));
          trace.hasSpansSatisfyingExactly(
              span ->
                  span.hasName("BatchJob itemsAndTaskletJob")
                      .hasKind(SpanKind.INTERNAL)
                      .hasAttribute(AttributeKey.stringKey("job.system"), "spring_batch"),
              span ->
                  span.hasName("BatchJob itemsAndTaskletJob.itemStep")
                      .hasKind(SpanKind.INTERNAL)
                      .hasTotalAttributeCount(0)
                      .hasParent(trace.getSpan(0)),
              chunk,
              chunk,
              chunk,
              span ->
                  span.hasName("BatchJob itemsAndTaskletJob.taskletStep")
                      .hasKind(SpanKind.INTERNAL)
                      .hasTotalAttributeCount(0)
                      .hasParent(trace.getSpan(0)),
              span ->
                  span.hasName("BatchJob itemsAndTaskletJob.taskletStep.Tasklet")
                      .hasKind(SpanKind.INTERNAL)
                      .hasTotalAttributeCount(0)
                      .hasParent(trace.getSpan(5)));
        });
  }

  @Test
  void shouldTraceFlowJob() {
    runner.runJob("flowJob");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("BatchJob flowJob")
                        .hasKind(SpanKind.INTERNAL)
                        .hasAttribute(AttributeKey.stringKey("job.system"), "spring_batch"),
                span ->
                    span.hasName("BatchJob flowJob.flowStep1")
                        .hasKind(SpanKind.INTERNAL)
                        .hasTotalAttributeCount(0)
                        .hasParent(trace.getSpan(0)),
                span ->
                    span.hasName("BatchJob flowJob.flowStep1.Tasklet")
                        .hasKind(SpanKind.INTERNAL)
                        .hasTotalAttributeCount(0)
                        .hasParent(trace.getSpan(1)),
                span ->
                    span.hasName("BatchJob flowJob.flowStep2")
                        .hasKind(SpanKind.INTERNAL)
                        .hasTotalAttributeCount(0)
                        .hasParent(trace.getSpan(0)),
                span ->
                    span.hasName("BatchJob flowJob.flowStep2.Tasklet")
                        .hasKind(SpanKind.INTERNAL)
                        .hasTotalAttributeCount(0)
                        .hasParent(trace.getSpan(3))));
  }

  @Test
  void shouldTraceSplitFlowJob() {
    runner.runJob("splitJob");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("BatchJob splitJob")
                        .hasKind(SpanKind.INTERNAL)
                        .hasAttribute(AttributeKey.stringKey("job.system"), "spring_batch"),
                span ->
                    span.satisfies(
                            spanData ->
                                assertThat(spanData.getName())
                                    .matches("BatchJob splitJob.splitFlowStep[12]"))
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0)),
                span ->
                    span.satisfies(
                            spanData ->
                                assertThat(spanData.getName())
                                    .matches("BatchJob splitJob.splitFlowStep[12].Tasklet"))
                        .hasKind(SpanKind.INTERNAL)
                        .hasTotalAttributeCount(0)
                        .hasParent(trace.getSpan(1)),
                span ->
                    span.satisfies(
                            spanData ->
                                assertThat(spanData.getName())
                                    .matches("BatchJob splitJob.splitFlowStep[12]"))
                        .hasKind(SpanKind.INTERNAL)
                        .hasTotalAttributeCount(0)
                        .hasParent(trace.getSpan(0)),
                span ->
                    span.satisfies(
                            spanData ->
                                assertThat(spanData.getName())
                                    .matches("BatchJob splitJob.splitFlowStep[12].Tasklet"))
                        .hasKind(SpanKind.INTERNAL)
                        .hasTotalAttributeCount(0)
                        .hasParent(trace.getSpan(3))));
  }

  @Test
  void shouldTraceJobWithDecision() {
    runner.runJob("decisionJob");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("BatchJob decisionJob")
                        .hasKind(SpanKind.INTERNAL)
                        .hasAttribute(AttributeKey.stringKey("job.system"), "spring_batch"),
                span ->
                    span.hasName("BatchJob decisionJob.decisionStepStart")
                        .hasKind(SpanKind.INTERNAL)
                        .hasTotalAttributeCount(0)
                        .hasParent(trace.getSpan(0)),
                span ->
                    span.hasName("BatchJob decisionJob.decisionStepStart.Tasklet")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(1)),
                span ->
                    span.hasName("BatchJob decisionJob.decisionStepLeft")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0)),
                span ->
                    span.hasName("BatchJob decisionJob.decisionStepLeft.Tasklet")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(3))));
  }

  @Test
  void shouldTracePartitionedJob() {
    runner.runJob("partitionedJob");

    testing.waitAndAssertTraces(
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span ->
                  span.hasName("BatchJob partitionedJob")
                      .hasKind(SpanKind.INTERNAL)
                      .hasAttribute(AttributeKey.stringKey("job.system"), "spring_batch"),
              span ->
                  span.hasName(
                          hasPartitionManagerStep()
                              ? "BatchJob partitionedJob.partitionManagerStep"
                              : "BatchJob partitionedJob.partitionWorkerStep")
                      .hasKind(SpanKind.INTERNAL)
                      .hasParent(trace.getSpan(0)),
              span ->
                  span.satisfies(
                          spanData ->
                              assertThat(spanData.getName())
                                  .matches(
                                      "BatchJob partitionedJob.partitionWorkerStep:partition[01]"))
                      .hasKind(SpanKind.INTERNAL)
                      .hasParent(trace.getSpan(1)),
              span -> partitionChunk(trace, span, 2),
              span -> partitionChunk(trace, span, 2),
              span ->
                  span.satisfies(
                          spanData ->
                              assertThat(spanData.getName())
                                  .matches(
                                      "BatchJob partitionedJob.partitionWorkerStep:partition[01]"))
                      .hasParent(trace.getSpan(1)),
              span -> partitionChunk(trace, span, 5),
              span -> partitionChunk(trace, span, 5));
        });
  }

  private static void partitionChunk(TraceAssert trace, SpanDataAssert span, int index) {
    span.satisfies(
            spanData ->
                assertThat(spanData.getName())
                    .matches("BatchJob partitionedJob.partitionWorkerStep:partition[01].Chunk"))
        .hasParent(trace.getSpan(index));
  }

  protected boolean hasPartitionManagerStep() {
    return true;
  }
}
