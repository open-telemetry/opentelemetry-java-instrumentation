/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package jsr

import io.opentelemetry.api.trace.Span

import javax.batch.api.chunk.listener.ChunkListener

class CustomEventChunkListener implements ChunkListener {
  @Override
  void beforeChunk() throws Exception {
    Span.current().addEvent("chunk.before")
  }

  @Override
  void onError(Exception e) throws Exception {
    Span.current().addEvent("chunk.error")
  }

  @Override
  void afterChunk() throws Exception {
    Span.current().addEvent("chunk.after")
  }
}
