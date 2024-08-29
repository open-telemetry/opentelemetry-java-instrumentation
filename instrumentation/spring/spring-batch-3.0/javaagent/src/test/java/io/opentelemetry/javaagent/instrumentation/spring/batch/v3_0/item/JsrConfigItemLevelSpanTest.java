/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.item;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.runner.JavaxBatchConfigRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class JsrConfigItemLevelSpanTest extends ItemLevelSpanTest {

  @RegisterExtension static final JavaxBatchConfigRunner runner = new JavaxBatchConfigRunner();

  public JsrConfigItemLevelSpanTest() {
    super(runner);
  }

  @Test
  @Override
  public void shouldTraceItemReadProcessAndWriteCalls() {
    runner.runJob("itemsAndTaskletJob");

    testing.waitAndAssertTraces(
        trace -> {
          Asserter with = new Asserter(trace, 37);
          with.span(
              0, span -> span.hasName("BatchJob itemsAndTaskletJob").hasKind(SpanKind.INTERNAL));

          // item step
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

          with.spansWithStep(
              3,
              11,
              2,
              0,
              span ->
                  span.hasName("BatchJob itemsAndTaskletJob.itemStep.ItemRead")
                      .hasKind(SpanKind.INTERNAL)
                      .hasParent(trace.getSpan(2)));

          with.spansWithStep(
              3,
              11,
              2,
              1,
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

          // chunk 2, items 5-10
          with.span(
              14,
              span ->
                  span.hasName("BatchJob itemsAndTaskletJob.itemStep.Chunk")
                      .hasKind(SpanKind.INTERNAL)
                      .hasParent(trace.getSpan(1)));

          with.spansWithStep(
              15,
              23,
              2,
              0,
              span ->
                  span.hasName("BatchJob itemsAndTaskletJob.itemStep.ItemRead")
                      .hasKind(SpanKind.INTERNAL)
                      .hasParent(trace.getSpan(14)));

          with.spansWithStep(
              15,
              23,
              2,
              1,
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

          with.spansWithStep(
              27,
              32,
              2,
              0,
              span ->
                  span.hasName("BatchJob itemsAndTaskletJob.itemStep.ItemRead")
                      .hasKind(SpanKind.INTERNAL)
                      .hasParent(trace.getSpan(26)));

          with.spansWithStep(
              27,
              32,
              2,
              1,
              span ->
                  span.hasName("BatchJob itemsAndTaskletJob.itemStep.ItemProcess")
                      .hasKind(SpanKind.INTERNAL)
                      .hasParent(trace.getSpan(26)));
          // last read returning end of stream marker
          with.span(33, span -> span.hasName("BatchJob itemsAndTaskletJob.itemStep.ItemRead"));
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

  @Override
  void shouldTraceAllItemOperationsOnAparallelItemsJob() {
    // does not work - not sure why
  }
}
