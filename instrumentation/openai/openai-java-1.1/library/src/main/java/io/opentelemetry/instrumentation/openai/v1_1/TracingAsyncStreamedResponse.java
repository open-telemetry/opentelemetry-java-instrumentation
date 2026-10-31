/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.openai.v1_1;

import com.openai.core.http.AsyncStreamResponse;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

final class TracingAsyncStreamedResponse<T> implements AsyncStreamResponse<T> {

  private final AsyncStreamResponse<T> delegate;
  private final ChunkListener<T> listener;

  TracingAsyncStreamedResponse(AsyncStreamResponse<T> delegate, ChunkListener<T> listener) {
    this.delegate = delegate;
    this.listener = listener;
  }

  @Override
  public void close() {
    listener.endSpan(null);
    delegate.close();
  }

  @Override
  public AsyncStreamResponse<T> subscribe(Handler<? super T> handler) {
    delegate.subscribe(new TracingHandler(handler));
    return this;
  }

  @Override
  public AsyncStreamResponse<T> subscribe(Handler<? super T> handler, Executor executor) {
    delegate.subscribe(new TracingHandler(handler), executor);
    return this;
  }

  @Override
  public CompletableFuture<Void> onCompleteFuture() {
    return delegate.onCompleteFuture();
  }

  private class TracingHandler implements Handler<T> {

    private final Handler<? super T> delegate;

    private TracingHandler(Handler<? super T> delegate) {
      this.delegate = delegate;
    }

    @Override
    public void onNext(T chunk) {
      listener.onChunk(chunk);
      delegate.onNext(chunk);
    }

    @Override
    public void onComplete(Optional<Throwable> error) {
      listener.endSpan(error.orElse(null));
      delegate.onComplete(error);
    }
  }
}
