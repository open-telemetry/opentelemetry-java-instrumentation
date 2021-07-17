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
import org.springframework.batch.core.step.builder.SimpleStepBuilder;

public class ChunkExecutionTracer extends BaseTracer {
  private static final ChunkExecutionTracer TRACER = new ChunkExecutionTracer();

  public static ChunkExecutionTracer tracer() {
    return TRACER;
  }

  public Context startSpan(ChunkContext chunkContext, Class<?> builderClass) {
    Context parentContext = Context.current();
    SpanBuilder spanBuilder =
        spanBuilder(parentContext, spanName(chunkContext, builderClass), INTERNAL);
    if (shouldCreateRootSpanForChunk()) {
      linkParentSpan(spanBuilder, parentContext);
    }
    return parentContext.with(spanBuilder.startSpan());
  }

  private static String spanName(ChunkContext chunkContext, Class<?> builderClass) {
    String jobName = chunkContext.getStepContext().getJobName();
    String stepName = chunkContext.getStepContext().getStepName();
    // only use "Chunk" for item processing steps, steps that use custom Tasklets will get "Tasklet"
    String type = SimpleStepBuilder.class.isAssignableFrom(builderClass) ? "Chunk" : "Tasklet";
    return "BatchJob " + jobName + "." + stepName + "." + type;
  }

  private static void linkParentSpan(SpanBuilder spanBuilder, Context parentContext) {
    spanBuilder.setNoParent();

    SpanContext parentSpanContext = Span.fromContext(parentContext).getSpanContext();
    if (parentSpanContext.isValid()) {
      spanBuilder.addLink(parentSpanContext);
    }
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.spring-batch-3.0";
  }
}
