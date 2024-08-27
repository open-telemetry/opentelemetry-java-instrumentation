/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.jsr;

import io.opentelemetry.api.trace.Span;
import javax.batch.api.chunk.listener.ChunkListener;

public class CustomEventChunkListener implements ChunkListener {
  @Override
  public void beforeChunk() {
    Span.current().addEvent("chunk.before");
  }

  @Override
  public void onError(Exception e) {
    Span.current().addEvent("chunk.error");
  }

  @Override
  public void afterChunk() {
    Span.current().addEvent("chunk.after");
  }
}
