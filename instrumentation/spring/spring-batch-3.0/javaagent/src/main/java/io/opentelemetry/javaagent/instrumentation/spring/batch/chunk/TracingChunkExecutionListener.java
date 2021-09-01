/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.batch.chunk;

import static io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge.rootContext;
import static io.opentelemetry.javaagent.instrumentation.spring.batch.SpringBatchInstrumentationConfig.shouldCreateRootSpanForChunk;
import static io.opentelemetry.javaagent.instrumentation.spring.batch.chunk.ChunkSingletons.chunkInstrumenter;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.instrumentation.api.ContextStore;
import io.opentelemetry.javaagent.instrumentation.spring.batch.ContextAndScope;
import javax.annotation.Nullable;
import org.springframework.batch.core.ChunkListener;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.core.Ordered;

public final class TracingChunkExecutionListener implements ChunkListener, Ordered {
  private final ContextStore<ChunkContext, ContextAndScope> executionContextStore;
  private final Class<?> builderClass;
  private ChunkContextAndBuilder chunkContextAndBuilder;

  public TracingChunkExecutionListener(
      ContextStore<ChunkContext, ContextAndScope> executionContextStore, Class<?> builderClass) {
    this.executionContextStore = executionContextStore;
    this.builderClass = builderClass;
  }

  @Override
  public void beforeChunk(ChunkContext chunkContext) {
    Context parentContext = shouldCreateRootSpanForChunk() ? rootContext() : currentContext();
    chunkContextAndBuilder = new ChunkContextAndBuilder(chunkContext, builderClass);
    if (!chunkInstrumenter().shouldStart(parentContext, chunkContextAndBuilder)) {
      return;
    }

    Context context = chunkInstrumenter().start(parentContext, chunkContextAndBuilder);
    // beforeJob & afterJob always execute on the same thread
    Scope scope = context.makeCurrent();
    executionContextStore.put(chunkContext, new ContextAndScope(context, scope));
  }

  @Override
  public void afterChunk(ChunkContext chunkContext) {
    end(chunkContext, null);
  }

  @Override
  public void afterChunkError(ChunkContext chunkContext) {
    Throwable throwable =
        (Throwable) chunkContext.getAttribute(ChunkListener.ROLLBACK_EXCEPTION_KEY);
    end(chunkContext, throwable);
  }

  private void end(ChunkContext chunkContext, @Nullable Throwable throwable) {
    ContextAndScope contextAndScope = executionContextStore.get(chunkContext);
    if (contextAndScope == null) {
      return;
    }

    executionContextStore.put(chunkContext, null);
    contextAndScope.closeScope();
    chunkInstrumenter().end(contextAndScope.getContext(), chunkContextAndBuilder, null, throwable);
  }

  @Override
  public int getOrder() {
    return HIGHEST_PRECEDENCE;
  }

  // equals() and hashCode() methods guarantee that only one instance of
  // TracingJobExecutionListener will be present in an ordered set of listeners

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    return o instanceof TracingChunkExecutionListener;
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
