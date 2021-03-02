/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.batch.item;

import static io.opentelemetry.api.trace.SpanKind.INTERNAL;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.springframework.batch.core.scope.context.ChunkContext;

public class ItemTracer extends BaseTracer {
  private static final ContextKey<ChunkContext> CHUNK_CONTEXT_KEY =
      ContextKey.named("opentelemetry-spring-batch-chunk-context-context-key");

  private static final ItemTracer TRACER = new ItemTracer();

  public static ItemTracer tracer() {
    return TRACER;
  }

  /**
   * Item-level listeners do not receive chunk/step context as parameters. Fortunately the whole
   * chunk always executes on one thread - in Spring Batch chunk is almost synonymous with a DB
   * transaction; this makes {@link ChunkContext} a good candidate to be stored in {@link Context}.
   */
  public Context startChunk(ChunkContext chunkContext) {
    return Context.current().with(CHUNK_CONTEXT_KEY, chunkContext);
  }

  @Nullable
  public Context startReadSpan() {
    return startItemSpan("ItemRead");
  }

  @Nullable
  public Context startProcessSpan() {
    return startItemSpan("ItemProcess");
  }

  @Nullable
  public Context startWriteSpan() {
    return startItemSpan("ItemWrite");
  }

  @Nullable
  private Context startItemSpan(String itemOperationName) {
    Context currentContext = Context.current();

    ChunkContext chunkContext = currentContext.get(CHUNK_CONTEXT_KEY);
    if (chunkContext == null) {
      return null;
    }

    String jobName = chunkContext.getStepContext().getJobName();
    String stepName = chunkContext.getStepContext().getStepName();

    return startSpan(
        currentContext, "BatchJob " + jobName + "." + stepName + "." + itemOperationName, INTERNAL);
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.spring-batch-3.0";
  }
}
