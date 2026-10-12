/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.openai.v1_1;

import com.openai.core.http.StreamResponse;
import java.util.Comparator;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.annotation.Nullable;

final class TracingStreamedResponse<T> implements StreamResponse<T> {

  private final StreamResponse<T> delegate;
  private final ChunkListener<T> listener;

  TracingStreamedResponse(StreamResponse<T> delegate, ChunkListener<T> listener) {
    this.delegate = delegate;
    this.listener = listener;
  }

  @Override
  public Stream<T> stream() {
    return StreamSupport.stream(new TracingSpliterator(delegate.stream().spliterator()), false);
  }

  @Override
  public void close() {
    listener.endSpan(null);
    delegate.close();
  }

  private class TracingSpliterator implements Spliterator<T> {

    private final Spliterator<T> delegateSpliterator;

    private TracingSpliterator(Spliterator<T> delegateSpliterator) {
      this.delegateSpliterator = delegateSpliterator;
    }

    @Override
    public boolean tryAdvance(Consumer<? super T> action) {
      boolean chunkReceived =
          delegateSpliterator.tryAdvance(
              chunk -> {
                listener.onChunk(chunk);
                action.accept(chunk);
              });
      if (!chunkReceived) {
        listener.endSpan(null);
      }
      return chunkReceived;
    }

    @Override
    @Nullable
    public Spliterator<T> trySplit() {
      // do not support parallelism to reliably catch the last chunk
      return null;
    }

    @Override
    public long estimateSize() {
      return delegateSpliterator.estimateSize();
    }

    @Override
    public long getExactSizeIfKnown() {
      return delegateSpliterator.getExactSizeIfKnown();
    }

    @Override
    public int characteristics() {
      return delegateSpliterator.characteristics();
    }

    @Override
    public Comparator<? super T> getComparator() {
      return delegateSpliterator.getComparator();
    }
  }
}
