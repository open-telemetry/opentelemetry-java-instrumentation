/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.item;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.runner.JobRunner;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.assertj.core.api.AssertAccess;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.AbstractJob;
import org.springframework.batch.core.step.tasklet.TaskletStep;
import org.springframework.batch.repeat.policy.SimpleCompletionPolicy;
import org.springframework.batch.repeat.support.TaskExecutorRepeatTemplate;

abstract class ItemLevelSpanTest {
  private final JobRunner runner;

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  ItemLevelSpanTest(JobRunner runner) {
    this.runner = runner;
  }

  @Test
  void shouldTraceItemReadProcessAndWriteCalls() {
    runner.runJob("itemsAndTaskletJob");

    testing.waitAndAssertTraces(
        trace -> {
          List<Consumer<SpanDataAssert>> assertions = new ArrayList<>();
          assertions.add(
              span -> span.hasName("BatchJob itemsAndTaskletJob").hasKind(SpanKind.INTERNAL));

          // item step
          assertions.add(
              span ->
                  span.hasName("BatchJob itemsAndTaskletJob.itemStep")
                      .hasKind(SpanKind.INTERNAL)
                      .hasParent(trace.getSpan(0)));

          // chunk 1, items 0-5
          assertions.add(
              span ->
                  span.hasName("BatchJob itemsAndTaskletJob.itemStep.Chunk")
                      .hasKind(SpanKind.INTERNAL)
                      .hasParent(trace.getSpan(1)));
          for (int i = 3; i <= 7; i++) {
            assertions.add(
                span ->
                    span.hasName("BatchJob itemsAndTaskletJob.itemStep.ItemRead")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(2)));
          }
          for (int i = 8; i <= 12; i++) {
            assertions.add(
                span ->
                    span.hasName("BatchJob itemsAndTaskletJob.itemStep.ItemProcess")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(2)));
          }
          assertions.add(
              span ->
                  span.hasName("BatchJob itemsAndTaskletJob.itemStep.ItemWrite")
                      .hasKind(SpanKind.INTERNAL)
                      .hasParent(trace.getSpan(2)));

          // chunk 2, items 5-10
          assertions.add(
              span ->
                  span.hasName("BatchJob itemsAndTaskletJob.itemStep.Chunk")
                      .hasKind(SpanKind.INTERNAL)
                      .hasParent(trace.getSpan(1)));
          for (int i = 15; i <= 19; i++) {
            assertions.add(
                span ->
                    span.hasName("BatchJob itemsAndTaskletJob.itemStep.ItemRead")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(14)));
          }
          for (int i = 20; i <= 24; i++) {
            assertions.add(
                span ->
                    span.hasName("BatchJob itemsAndTaskletJob.itemStep.ItemProcess")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(14)));
          }
          assertions.add(
              span ->
                  span.hasName("BatchJob itemsAndTaskletJob.itemStep.ItemWrite")
                      .hasKind(SpanKind.INTERNAL)
                      .hasParent(trace.getSpan(14)));

          // chunk 3, items 10-13
          assertions.add(
              span ->
                  span.hasName("BatchJob itemsAndTaskletJob.itemStep.Chunk")
                      .hasKind(SpanKind.INTERNAL)
                      .hasParent(trace.getSpan(1)));
          // +1 for last read returning end of stream marker
          for (int i = 27; i <= 30; i++) {
            assertions.add(
                span ->
                    span.hasName("BatchJob itemsAndTaskletJob.itemStep.ItemRead")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(26)));
          }
          for (int i = 31; i <= 33; i++) {
            assertions.add(
                span ->
                    span.hasName("BatchJob itemsAndTaskletJob.itemStep.ItemProcess")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(26)));
          }
          assertions.add(
              span ->
                  span.hasName("BatchJob itemsAndTaskletJob.itemStep.ItemWrite")
                      .hasKind(SpanKind.INTERNAL)
                      .hasParent(trace.getSpan(26)));

          // tasklet step
          assertions.add(
              span ->
                  span.hasName("BatchJob itemsAndTaskletJob.taskletStep")
                      .hasKind(SpanKind.INTERNAL)
                      .hasParent(trace.getSpan(0)));
          assertions.add(
              span ->
                  span.hasName("BatchJob itemsAndTaskletJob.taskletStep.Tasklet")
                      .hasKind(SpanKind.INTERNAL)
                      .hasParent(trace.getSpan(35)));

          trace.hasSpansSatisfyingExactly(assertions);
        });
  }

  @Test
  void shouldTraceAllItemOperationsOnAparallelItemsJob() {
    runner.runJob("parallelItemsJob");

    testing.waitAndAssertTraces(
        trace -> {
          // as chunks are processed in parallel we need to sort them to guarantee that they are
          // in the expected order
          // firstly compute child span count for each chunk, we'll sort chunks from larger to
          // smaller based on child count

          List<SpanData> spans = AssertAccess.getActual(trace);
          Map<SpanData, Long> childCount = new HashMap<>();

          for (SpanData span : spans) {
            if (span.getName().equals("BatchJob parallelItemsJob.parallelItemsStep.Chunk")) {
              childCount.put(
                  span,
                  spans.stream()
                      .filter(it -> it.getParentSpanId().equals(span.getSpanId()))
                      .count());
            }
          }

          spans.sort(
              Comparator.comparingLong(
                  it -> {
                    // job span is first
                    if (it.getName().equals("BatchJob parallelItemsJob")) {
                      return 0;
                    }
                    // step span is second
                    if (it.getName().equals("BatchJob parallelItemsJob.parallelItemsStep")) {
                      return 1;
                    }

                    // find the chunk this span belongs to
                    SpanData chunkSpan = it;
                    while (chunkSpan != null
                        && !chunkSpan
                            .getName()
                            .equals("BatchJob parallelItemsJob.parallelItemsStep.Chunk")) {
                      SpanData currentChunkSpan = chunkSpan;
                      chunkSpan =
                          spans.stream()
                              .filter(
                                  candidate ->
                                      candidate
                                          .getSpanId()
                                          .equals(currentChunkSpan.getParentSpanId()))
                              .findFirst()
                              .orElse(null);
                    }
                    if (chunkSpan != null) {
                      // sort larger chunks first
                      return 100 - childCount.get(chunkSpan);
                    }
                    throw new IllegalStateException("item spans should have a parent chunk span");
                  }));

          List<Consumer<SpanDataAssert>> assertions = new ArrayList<>();
          assertions.add(
              span -> span.hasName("BatchJob parallelItemsJob").hasKind(SpanKind.INTERNAL));
          assertions.add(
              span ->
                  span.hasName("BatchJob parallelItemsJob.parallelItemsStep")
                      .hasKind(SpanKind.INTERNAL)
                      .hasParent(trace.getSpan(0)));

          // chunk 1, first two items; thread 1
          assertions.add(
              span ->
                  span.hasName("BatchJob parallelItemsJob.parallelItemsStep.Chunk")
                      .hasKind(SpanKind.INTERNAL)
                      .hasParent(trace.getSpan(1)));
          for (int i = 3; i <= 4; i++) {
            assertions.add(
                span ->
                    span.hasName("BatchJob parallelItemsJob.parallelItemsStep.ItemRead")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(2)));
          }
          for (int i = 5; i <= 6; i++) {
            assertions.add(
                span ->
                    span.hasName("BatchJob parallelItemsJob.parallelItemsStep.ItemProcess")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(2)));
          }
          assertions.add(
              span ->
                  span.hasName("BatchJob parallelItemsJob.parallelItemsStep.ItemWrite")
                      .hasKind(SpanKind.INTERNAL)
                      .hasParent(trace.getSpan(2)));

          // chunk 2, items 3 & 4; thread 2
          assertions.add(
              span ->
                  span.hasName("BatchJob parallelItemsJob.parallelItemsStep.Chunk")
                      .hasKind(SpanKind.INTERNAL)
                      .hasParent(trace.getSpan(1)));
          for (int i = 9; i <= 10; i++) {
            assertions.add(
                span ->
                    span.hasName("BatchJob parallelItemsJob.parallelItemsStep.ItemRead")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(8)));
          }
          for (int i = 11; i <= 12; i++) {
            assertions.add(
                span ->
                    span.hasName("BatchJob parallelItemsJob.parallelItemsStep.ItemProcess")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(8)));
          }
          assertions.add(
              span ->
                  span.hasName("BatchJob parallelItemsJob.parallelItemsStep.ItemWrite")
                      .hasKind(SpanKind.INTERNAL)
                      .hasParent(trace.getSpan(8)));

          // chunk 3, 5th item; thread 1
          assertions.add(
              span ->
                  span.hasName("BatchJob parallelItemsJob.parallelItemsStep.Chunk")
                      .hasKind(SpanKind.INTERNAL)
                      .hasParent(trace.getSpan(1)));
          // +1 for last read returning end of stream marker
          for (int i = 15; i <= 16; i++) {
            assertions.add(
                span ->
                    span.hasName("BatchJob parallelItemsJob.parallelItemsStep.ItemRead")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(14)));
          }
          assertions.add(
              span ->
                  span.hasName("BatchJob parallelItemsJob.parallelItemsStep.ItemProcess")
                      .hasKind(SpanKind.INTERNAL)
                      .hasParent(trace.getSpan(14)));
          assertions.add(
              span ->
                  span.hasName("BatchJob parallelItemsJob.parallelItemsStep.ItemWrite")
                      .hasKind(SpanKind.INTERNAL)
                      .hasParent(trace.getSpan(14)));

          trace.hasSpansSatisfyingExactly(assertions);
        });
  }

  protected void postProcessParallelItemsJob(String jobName, Job job) {
    if ("parallelItemsJob".equals(jobName)) {
      Step step = ((AbstractJob) job).getStep("parallelItemsStep");
      TaskletStep taskletStep = (TaskletStep) step;
      // explicitly set the number of chunks we expect from this test to ensure we always get
      // the same number of spans
      try {
        Field field = taskletStep.getClass().getDeclaredField("stepOperations");
        field.setAccessible(true);
        TaskExecutorRepeatTemplate stepOperations =
            (TaskExecutorRepeatTemplate) field.get(taskletStep);
        stepOperations.setCompletionPolicy(new SimpleCompletionPolicy(3));
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
    }
  }
}
