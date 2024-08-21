/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.item;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.runner.JobRunner;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.lang.reflect.Field;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

  public ItemLevelSpanTest(JobRunner runner) {
    this.runner = runner;
  }

  @Test
  void shouldTraceItemReadProcessAndWriteCalls() {
    runner.runJob("itemsAndTaskletJob");

    testing.waitAndAssertTraces(
        trace -> {
          Asserter with = new Asserter(trace, 37);
          with.span(
              0, span -> span.hasName("BatchJob itemsAndTaskletJob").hasKind(SpanKind.INTERNAL));
          with.span(
              1,
              span ->
                  span.hasName("BatchJob itemsAndTaskletJob.itemStep")
                      .hasKind(SpanKind.INTERNAL)
                      .hasParent(trace.getSpan(0)));

          // chunk 1, items 0-5
          with.span(
              2,
              span ->
                  span.hasName("BatchJob itemsAndTaskletJob.itemStep.Chunk")
                      .hasKind(SpanKind.INTERNAL)
                      .hasParent(trace.getSpan(1)));
          with.spans(
              3,
              7,
              span ->
                  span.hasName("BatchJob itemsAndTaskletJob.itemStep.ItemRead")
                      .hasKind(SpanKind.INTERNAL)
                      .hasParent(trace.getSpan(2)));
          with.spans(
              8,
              12,
              span ->
                  span.hasName("BatchJob itemsAndTaskletJob.itemStep.ItemProcess")
                      .hasKind(SpanKind.INTERNAL)
                      .hasParent(trace.getSpan(2)));
          with.span(
              13,
              span ->
                  span.hasName("BatchJob itemsAndTaskletJob.itemStep.ItemWrite")
                      .hasKind(SpanKind.INTERNAL)
                      .hasParent(trace.getSpan(2)));
          with.span(
              14,
              span ->
                  span.hasName("BatchJob itemsAndTaskletJob.itemStep.Chunk")
                      .hasKind(SpanKind.INTERNAL)
                      .hasParent(trace.getSpan(1)));
          with.spans(
              15,
              19,
              span ->
                  span.hasName("BatchJob itemsAndTaskletJob.itemStep.ItemRead")
                      .hasKind(SpanKind.INTERNAL)
                      .hasParent(trace.getSpan(14)));
          with.spans(
              20,
              24,
              span ->
                  span.hasName("BatchJob itemsAndTaskletJob.itemStep.ItemProcess")
                      .hasKind(SpanKind.INTERNAL)
                      .hasParent(trace.getSpan(14)));
          with.span(
              25,
              span ->
                  span.hasName("BatchJob itemsAndTaskletJob.itemStep.ItemWrite")
                      .hasKind(SpanKind.INTERNAL)
                      .hasParent(trace.getSpan(14)));
          // chunk 3, items 10-13
          with.span(
              26,
              span ->
                  span.hasName("BatchJob itemsAndTaskletJob.itemStep.Chunk")
                      .hasKind(SpanKind.INTERNAL)
                      .hasParent(trace.getSpan(1)));
          // +1 for last read returning end of stream marker
          with.spans(
              27,
              30,
              span ->
                  span.hasName("BatchJob itemsAndTaskletJob.itemStep.ItemRead")
                      .hasKind(SpanKind.INTERNAL)
                      .hasParent(trace.getSpan(26)));
          with.spans(
              31,
              33,
              span ->
                  span.hasName("BatchJob itemsAndTaskletJob.itemStep.ItemProcess")
                      .hasKind(SpanKind.INTERNAL)
                      .hasParent(trace.getSpan(26)));
          with.span(
              34,
              span ->
                  span.hasName("BatchJob itemsAndTaskletJob.itemStep.ItemWrite")
                      .hasKind(SpanKind.INTERNAL)
                      .hasParent(trace.getSpan(26)));
          // tasklet step
          with.span(
              35,
              span ->
                  span.hasName("BatchJob itemsAndTaskletJob.taskletStep")
                      .hasKind(SpanKind.INTERNAL)
                      .hasParent(trace.getSpan(0)));
          with.span(
              36,
              span ->
                  span.hasName("BatchJob itemsAndTaskletJob.taskletStep.Tasklet")
                      .hasKind(SpanKind.INTERNAL)
                      .hasParent(trace.getSpan(35)));
        });
  }

  @Test
  void shouldTraceAllItemOperationsOnAParallelItemsJob() {
    runner.runJob("parallelItemsJob");

    testing.waitAndAssertTraces(
        trace -> {
          Asserter with = new Asserter(trace, 19);

          // as chunks are processed in parallel we need to sort them to guarantee that they are
          // in the expected order
          // firstly compute child span count for each chunk, we'll sort chunks from larger to
          // smaller
          // based on child count
          List<SpanData> all = with.getAll();
          Map<SpanData, Number> childCount = new HashMap<>();
          all.forEach(
              span -> {
                if (span.getName().equals("BatchJob parallelItemsJob.parallelItemsStep.Chunk")) {
                  childCount.put(
                      span,
                      all.stream()
                          .filter(it -> it.getParentSpanId().equals(span.getSpanId()))
                          .count());
                }
              });
          // sort spans with a ranking function
          all.sort(
              Comparator.comparingInt(
                  span -> {
                    // job span is first
                    if (span.getName().equals("BatchJob parallelItemsJob")) {
                      return 0;
                    }
                    // step span is second
                    if (span.getName().equals("BatchJob parallelItemsJob.parallelItemsStep")) {
                      return 1;
                    }

                    // find the chunk this span belongs to
                    SpanData chunkSpan = span;
                    while (!chunkSpan
                        .getName()
                        .equals("BatchJob parallelItemsJob.parallelItemsStep.Chunk")) {
                      for (SpanData it : all) {
                        if (it.getSpanId().equals(chunkSpan.getParentSpanId())) {
                          chunkSpan = it;
                          break;
                        }
                      }
                    }
                    // sort larger chunks first
                    return 100 - childCount.get(chunkSpan).intValue();
                  }));
          with.setSortedSpans(all);

          with.span(
              0, span -> span.hasName("BatchJob parallelItemsJob").hasKind(SpanKind.INTERNAL));

          with.span(
              1,
              span ->
                  span.hasName("BatchJob parallelItemsJob.parallelItemsStep")
                      .hasKind(SpanKind.INTERNAL)
                      .hasParent(trace.getSpan(0)));

          // chunk 1, first two items; thread 1
          with.span(
              2,
              span ->
                  span.hasName("BatchJob parallelItemsJob.parallelItemsStep.Chunk")
                      .hasKind(SpanKind.INTERNAL)
                      .hasParent(trace.getSpan(1)));
          with.spans(
              3,
              4,
              span ->
                  span.hasName("BatchJob parallelItemsJob.parallelItemsStep.ItemRead")
                      .hasKind(SpanKind.INTERNAL)
                      .hasParent(trace.getSpan(2)));
          with.spans(
              5,
              6,
              span ->
                  span.hasName("BatchJob parallelItemsJob.parallelItemsStep.ItemProcess")
                      .hasKind(SpanKind.INTERNAL)
                      .hasParent(trace.getSpan(2)));
          with.span(
              7,
              span ->
                  span.hasName("BatchJob parallelItemsJob.parallelItemsStep.ItemWrite")
                      .hasKind(SpanKind.INTERNAL)
                      .hasParent(trace.getSpan(2)));

          // chunk 2, items 3 & 4; thread 2
          with.span(
              8,
              span ->
                  span.hasName("BatchJob parallelItemsJob.parallelItemsStep.Chunk")
                      .hasKind(SpanKind.INTERNAL)
                      .hasParent(trace.getSpan(1)));
          with.spans(
              9,
              10,
              span ->
                  span.hasName("BatchJob parallelItemsJob.parallelItemsStep.ItemRead")
                      .hasKind(SpanKind.INTERNAL)
                      .hasParent(trace.getSpan(8)));
          with.spans(
              11,
              12,
              span ->
                  span.hasName("BatchJob parallelItemsJob.parallelItemsStep.ItemProcess")
                      .hasKind(SpanKind.INTERNAL)
                      .hasParent(trace.getSpan(8)));
          with.span(
              13,
              span ->
                  span.hasName("BatchJob parallelItemsJob.parallelItemsStep.ItemWrite")
                      .hasKind(SpanKind.INTERNAL)
                      .hasParent(trace.getSpan(8)));

          // chunk 3, 5th item; thread 1
          with.span(
              14,
              span ->
                  span.hasName("BatchJob parallelItemsJob.parallelItemsStep.Chunk")
                      .hasKind(SpanKind.INTERNAL)
                      .hasParent(trace.getSpan(1)));
          // +1 for last read returning end of stream marker
          with.spans(
              15,
              16,
              span ->
                  span.hasName("BatchJob parallelItemsJob.parallelItemsStep.ItemRead")
                      .hasKind(SpanKind.INTERNAL)
                      .hasParent(trace.getSpan(14)));
          with.span(
              17,
              span ->
                  span.hasName("BatchJob parallelItemsJob.parallelItemsStep.ItemProcess")
                      .hasKind(SpanKind.INTERNAL)
                      .hasParent(trace.getSpan(14)));
          with.span(
              18,
              span ->
                  span.hasName("BatchJob parallelItemsJob.parallelItemsStep.ItemWrite")
                      .hasKind(SpanKind.INTERNAL)
                      .hasParent(trace.getSpan(14)));
        });
  }

  public void postProcessParallelItemsJob(String jobName, Job job) {
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
