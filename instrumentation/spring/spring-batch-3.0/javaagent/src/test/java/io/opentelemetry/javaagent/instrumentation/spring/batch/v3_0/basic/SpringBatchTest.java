/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.basic;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static java.util.Collections.singletonMap;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.runner.JobRunner;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.testing.assertj.TraceAssert;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.semconv.ExceptionAttributes;
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
                        .hasParent(trace.getSpan(0)),
                span ->
                    span.hasName("BatchJob taskletJob.step.Tasklet")
                        .hasKind(SpanKind.INTERNAL)
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
                        .hasParent(trace.getSpan(0)),
                span ->
                    verifyException(
                        span.hasName("BatchJob taskletJob.step.Tasklet")
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(1))
                            .hasStatus(StatusData.error()),
                        new IllegalStateException("fail"))));
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
                      .hasParent(trace.getSpan(1));
          trace.hasSpansSatisfyingExactly(
              span ->
                  span.hasName("BatchJob itemsAndTaskletJob")
                      .hasKind(SpanKind.INTERNAL)
                      .hasAttribute(AttributeKey.stringKey("job.system"), "spring_batch"),
              span ->
                  span.hasName("BatchJob itemsAndTaskletJob.itemStep")
                      .hasKind(SpanKind.INTERNAL)
                      .hasParent(trace.getSpan(0)),
              chunk,
              chunk,
              chunk,
              span ->
                  span.hasName("BatchJob itemsAndTaskletJob.taskletStep")
                      .hasKind(SpanKind.INTERNAL)
                      .hasParent(trace.getSpan(0)),
              span ->
                  span.hasName("BatchJob itemsAndTaskletJob.taskletStep.Tasklet")
                      .hasKind(SpanKind.INTERNAL)
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
                        .hasParent(trace.getSpan(0)),
                span ->
                    span.hasName("BatchJob flowJob.flowStep1.Tasklet")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(1)),
                span ->
                    span.hasName("BatchJob flowJob.flowStep2")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0)),
                span ->
                    span.hasName("BatchJob flowJob.flowStep2.Tasklet")
                        .hasKind(SpanKind.INTERNAL)
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
                        .hasParent(trace.getSpan(1)),
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
          int index = 2;
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
              span -> partitionChunk(trace, span, index),
              span -> partitionChunk(trace, span, index),
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

  // should be moved to SpanDataAssert
  private static void verifyException(SpanDataAssert span, Throwable exception) {
    span.hasStatus(StatusData.error())
        .hasEventsSatisfying(
            events ->
                assertThat(events.get(0))
                    .hasName("exception")
                    .hasAttributesSatisfying(
                        equalTo(ExceptionAttributes.EXCEPTION_TYPE, exception.getClass().getName()),
                        equalTo(ExceptionAttributes.EXCEPTION_MESSAGE, exception.getMessage()),
                        satisfies(
                            ExceptionAttributes.EXCEPTION_STACKTRACE,
                            val -> val.isInstanceOf(String.class))));
  }
}
