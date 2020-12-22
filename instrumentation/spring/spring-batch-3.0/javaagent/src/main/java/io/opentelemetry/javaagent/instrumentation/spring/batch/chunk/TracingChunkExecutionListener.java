/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.batch.chunk;

import static io.opentelemetry.javaagent.instrumentation.spring.batch.chunk.ChunkExecutionTracer.tracer;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.instrumentation.api.ContextStore;
import io.opentelemetry.javaagent.instrumentation.spring.batch.ContextAndScope;
import org.springframework.batch.core.ChunkListener;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.core.Ordered;

public class TracingChunkExecutionListener implements ChunkListener, Ordered {
  private final ContextStore<ChunkContext, ContextAndScope> executionContextStore;

  public TracingChunkExecutionListener(
      ContextStore<ChunkContext, ContextAndScope> executionContextStore) {
    this.executionContextStore = executionContextStore;
  }

  @Override
  public void beforeChunk(ChunkContext chunkContext) {
    Context context = tracer().startSpan(chunkContext);
    // beforeJob & afterJob always execute on the same thread
    Scope scope = context.makeCurrent();
    executionContextStore.put(chunkContext, new ContextAndScope(context, scope));
  }

  @Override
  public void afterChunk(ChunkContext chunkContext) {
    ContextAndScope contextAndScope = executionContextStore.get(chunkContext);
    if (contextAndScope != null) {
      executionContextStore.put(chunkContext, null);
      contextAndScope.closeScope();
      tracer().end(contextAndScope.getContext());
    }
  }

  @Override
  public void afterChunkError(ChunkContext chunkContext) {
    ContextAndScope contextAndScope = executionContextStore.get(chunkContext);
    if (contextAndScope != null) {
      executionContextStore.put(chunkContext, null);
      contextAndScope.closeScope();
      Throwable throwable =
          (Throwable) chunkContext.getAttribute(ChunkListener.ROLLBACK_EXCEPTION_KEY);
      tracer().endExceptionally(contextAndScope.getContext(), throwable);
    }
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
    return o != null && getClass() == o.getClass();
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
