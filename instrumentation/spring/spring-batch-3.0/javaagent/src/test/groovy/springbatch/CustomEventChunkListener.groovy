/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package springbatch

import io.opentelemetry.api.trace.Span
import org.springframework.batch.core.ChunkListener
import org.springframework.batch.core.scope.context.ChunkContext

class CustomEventChunkListener implements ChunkListener {
  @Override
  void beforeChunk(ChunkContext context) {
    Span.current().addEvent("chunk.before")
  }

  @Override
  void afterChunk(ChunkContext context) {
    Span.current().addEvent("chunk.after")
  }

  @Override
  void afterChunkError(ChunkContext context) {
    Span.current().addEvent("chunk.error")
  }
}
