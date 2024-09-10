/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.item;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.runner.JavaxBatchConfigRunner;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

class JsrConfigItemLevelSpanTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension static final JavaxBatchConfigRunner runner = new JavaxBatchConfigRunner();

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

          // chunk 2, items 6-10
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
                        .hasParent(trace.getSpan(3)));
          }
          for (int i = 20; i <= 24; i++) {
            assertions.add(
                span ->
                    span.hasName("BatchJob itemsAndTaskletJob.itemStep.ItemProcess")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(3)));
          }
          assertions.add(
              span ->
                  span.hasName("BatchJob itemsAndTaskletJob.itemStep.ItemWrite")
                      .hasKind(SpanKind.INTERNAL)
                      .hasParent(trace.getSpan(3)));

          // chunk 3, items 11-13
          assertions.add(
              span ->
                  span.hasName("BatchJob itemsAndTaskletJob.itemStep.Chunk")
                      .hasKind(SpanKind.INTERNAL)
                      .hasParent(trace.getSpan(1)));
          for (int i = 27; i <= 29; i++) {
            assertions.add(
                span ->
                    span.hasName("BatchJob itemsAndTaskletJob.itemStep.ItemRead")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(26)));
          }
          for (int i = 30; i <= 32; i++) {
            assertions.add(
                span ->
                    span.hasName("BatchJob itemsAndTaskletJob.itemStep.ItemProcess")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(26)));
          }

          // last read returning end of stream marker
          assertions.add(span -> span.hasName("BatchJob itemsAndTaskletJob.itemStep.ItemRead"));
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
}
