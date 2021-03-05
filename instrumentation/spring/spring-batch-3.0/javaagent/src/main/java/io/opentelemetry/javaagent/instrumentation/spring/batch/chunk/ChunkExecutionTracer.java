/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.batch.chunk;

import static io.opentelemetry.api.trace.SpanKind.INTERNAL;
import static io.opentelemetry.javaagent.instrumentation.spring.batch.SpringBatchInstrumentationConfig.shouldCreateRootSpanForChunk;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import org.springframework.batch.core.scope.context.ChunkContext;

public class ChunkExecutionTracer extends BaseTracer {
  private static final ChunkExecutionTracer TRACER = new ChunkExecutionTracer();

  public static ChunkExecutionTracer tracer() {
    return TRACER;
  }

  public Context startSpan(ChunkContext chunkContext) {
    String jobName = chunkContext.getStepContext().getJobName();
    String stepName = chunkContext.getStepContext().getStepName();
    SpanBuilder spanBuilder =
        tracer.spanBuilder("BatchJob " + jobName + "." + stepName + ".Chunk").setSpanKind(INTERNAL);
    if (shouldCreateRootSpanForChunk()) {
      linkParentSpan(spanBuilder);
    }
    Span span = spanBuilder.startSpan();
    return Context.current().with(span);
  }

  private void linkParentSpan(SpanBuilder spanBuilder) {
    spanBuilder.setNoParent();

    SpanContext parentSpanContext = Span.current().getSpanContext();
    if (parentSpanContext.isValid()) {
      spanBuilder.addLink(parentSpanContext);
    }
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.spring-batch-3.0";
  }
}
