/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.batch.chunk;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.spring.batch.SpringBatchInstrumentationConfig.instrumentationName;
import static io.opentelemetry.javaagent.instrumentation.spring.batch.SpringBatchInstrumentationConfig.shouldCreateRootSpanForChunk;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanLinksBuilder;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.builder.SimpleStepBuilder;

public class ChunkSingletons {

  private static final Instrumenter<ChunkContextAndBuilder, Void> INSTRUMENTER;

  static {
    InstrumenterBuilder<ChunkContextAndBuilder, Void> instrumenterBuilder =
        Instrumenter.builder(
            GlobalOpenTelemetry.get(), instrumentationName(), ChunkSingletons::spanName);

    if (shouldCreateRootSpanForChunk()) {
      instrumenterBuilder.addSpanLinksExtractor(ChunkSingletons::extractSpanLinks);
    }

    INSTRUMENTER = instrumenterBuilder.newInstrumenter();
  }

  public static Instrumenter<ChunkContextAndBuilder, Void> chunkInstrumenter() {
    return INSTRUMENTER;
  }

  private static void extractSpanLinks(
      SpanLinksBuilder spanLinks, Context unused, ChunkContextAndBuilder request) {
    // The context passed will be Context.root() if shouldCreateRootSpanForChunk()
    Context parentContext = currentContext();
    if (shouldCreateRootSpanForChunk()) {
      SpanContext parentSpanContext = Span.fromContext(parentContext).getSpanContext();
      if (parentSpanContext.isValid()) {
        spanLinks.addLink(parentSpanContext);
      }
    }
  }

  private static String spanName(ChunkContextAndBuilder chunkContextAndBuilder) {
    ChunkContext chunkContext = chunkContextAndBuilder.chunkContext;
    Class<?> builderClass = chunkContextAndBuilder.builderClass;

    String jobName = chunkContext.getStepContext().getJobName();
    String stepName = chunkContext.getStepContext().getStepName();
    // only use "Chunk" for item processing steps, steps that use custom Tasklets will get "Tasklet"
    String type = SimpleStepBuilder.class.isAssignableFrom(builderClass) ? "Chunk" : "Tasklet";
    return "BatchJob " + jobName + "." + stepName + "." + type;
  }

  private ChunkSingletons() {}
}
