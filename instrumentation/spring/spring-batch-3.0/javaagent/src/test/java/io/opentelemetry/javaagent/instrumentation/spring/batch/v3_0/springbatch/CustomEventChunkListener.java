/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.springbatch;

import io.opentelemetry.api.trace.Span;
import org.springframework.batch.core.ChunkListener;
import org.springframework.batch.core.scope.context.ChunkContext;

public class CustomEventChunkListener implements ChunkListener {
  @Override
  public void beforeChunk(ChunkContext context) {
    Span.current().addEvent("chunk.before");
  }

  @Override
  public void afterChunk(ChunkContext context) {
    Span.current().addEvent("chunk.after");
  }

  @Override
  public void afterChunkError(ChunkContext context) {
    Span.current().addEvent("chunk.error");
  }
}
