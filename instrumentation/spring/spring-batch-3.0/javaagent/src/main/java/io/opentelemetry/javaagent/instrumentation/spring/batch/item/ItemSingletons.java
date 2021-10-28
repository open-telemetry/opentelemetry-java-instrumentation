/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.batch.item;

import static io.opentelemetry.javaagent.instrumentation.spring.batch.SpringBatchInstrumentationConfig.instrumentationName;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import org.springframework.batch.core.scope.context.ChunkContext;

public class ItemSingletons {

  static final String ITEM_OPERATION_READ = "ItemRead";
  static final String ITEM_OPERATION_WRITE = "ItemWrite";
  static final String ITEM_OPERATION_PROCESS = "ItemProcess";

  private static final Instrumenter<String, Void> INSTRUMENTER =
      Instrumenter.<String, Void>builder(
              GlobalOpenTelemetry.get(), instrumentationName(), str -> str)
          .newInstrumenter();

  public static Instrumenter<String, Void> itemInstrumenter() {
    return INSTRUMENTER;
  }

  public static String itemName(ChunkContext chunkContext, String itemOperationName) {
    String jobName = chunkContext.getStepContext().getJobName();
    String stepName = chunkContext.getStepContext().getStepName();

    return "BatchJob " + jobName + "." + stepName + "." + itemOperationName;
  }

  private static final ContextKey<ChunkContext> CHUNK_CONTEXT_KEY =
      ContextKey.named("opentelemetry-spring-batch-chunk-context-context-key");

  /**
   * Item-level listeners do not receive chunk/step context as parameters. Fortunately the whole
   * chunk always executes on one thread - in Spring Batch chunk is almost synonymous with a DB
   * transaction; this makes {@link ChunkContext} a good candidate to be stored in {@link Context}.
   */
  public static Context startChunk(Context currentContext, ChunkContext chunkContext) {
    return currentContext.with(CHUNK_CONTEXT_KEY, chunkContext);
  }

  public static ChunkContext getChunkContext(Context currentContext) {
    return currentContext.get(CHUNK_CONTEXT_KEY);
  }

  private ItemSingletons() {}
}
