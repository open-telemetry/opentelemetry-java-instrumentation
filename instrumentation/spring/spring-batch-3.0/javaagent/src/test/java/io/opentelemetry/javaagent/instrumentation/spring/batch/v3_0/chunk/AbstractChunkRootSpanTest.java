/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.chunk;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.runner.JobRunner;
import io.opentelemetry.sdk.testing.assertj.TraceAssert;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

abstract class AbstractChunkRootSpanTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private final JobRunner jobRunner;

  AbstractChunkRootSpanTest(JobRunner jobRunner) {
    this.jobRunner = jobRunner;
  }

  @Test
  void shouldCreateSeparateTracesForEachChunk() {
    jobRunner.runJob("itemsAndTaskletJob");
    AtomicReference<SpanData> itemStepSpan = new AtomicReference<>();
    AtomicReference<SpanData> taskletStepSpan = new AtomicReference<>();

    Consumer<TraceAssert> chunk =
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("BatchJob itemsAndTaskletJob.itemStep.Chunk")
                        .hasKind(SpanKind.INTERNAL)
                        .hasLinks(LinkData.create(itemStepSpan.get().getSpanContext())));
    testing.waitAndAssertTraces(
        trace -> {
          itemStepSpan.set(trace.getSpan(1));
          taskletStepSpan.set(trace.getSpan(2));

          trace.hasSpansSatisfyingExactly(
              span -> span.hasName("BatchJob itemsAndTaskletJob").hasKind(SpanKind.INTERNAL),
              span ->
                  span.hasName("BatchJob itemsAndTaskletJob.itemStep")
                      .hasKind(SpanKind.INTERNAL)
                      .hasParent(trace.getSpan(0)),
              span ->
                  span.hasName("BatchJob itemsAndTaskletJob.taskletStep")
                      .hasKind(SpanKind.INTERNAL)
                      .hasParent(trace.getSpan(0)));
        },
        chunk,
        chunk,
        chunk,
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("BatchJob itemsAndTaskletJob.taskletStep.Tasklet")
                        .hasKind(SpanKind.INTERNAL)
                        .hasLinks(LinkData.create(taskletStepSpan.get().getSpanContext()))));
  }
}
