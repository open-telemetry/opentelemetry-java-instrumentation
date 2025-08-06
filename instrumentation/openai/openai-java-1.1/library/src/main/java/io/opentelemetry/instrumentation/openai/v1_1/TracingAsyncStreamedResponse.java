/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.openai.v1_1;

import com.openai.core.http.AsyncStreamResponse;
import com.openai.models.chat.completions.ChatCompletionChunk;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

final class TracingAsyncStreamedResponse implements AsyncStreamResponse<ChatCompletionChunk> {

  private final AsyncStreamResponse<ChatCompletionChunk> delegate;
  private final StreamListener listener;

  TracingAsyncStreamedResponse(
      AsyncStreamResponse<ChatCompletionChunk> delegate, StreamListener listener) {
    this.delegate = delegate;
    this.listener = listener;
  }

  @Override
  public void close() {
    listener.endSpan(null);
    delegate.close();
  }

  @Override
  public AsyncStreamResponse<ChatCompletionChunk> subscribe(
      Handler<? super ChatCompletionChunk> handler) {
    delegate.subscribe(new TracingHandler(handler));
    return this;
  }

  @Override
  public AsyncStreamResponse<ChatCompletionChunk> subscribe(
      Handler<? super ChatCompletionChunk> handler, Executor executor) {
    delegate.subscribe(new TracingHandler(handler), executor);
    return this;
  }

  @Override
  public CompletableFuture<Void> onCompleteFuture() {
    return delegate.onCompleteFuture();
  }

  private class TracingHandler implements Handler<ChatCompletionChunk> {

    private final Handler<? super ChatCompletionChunk> delegate;

    private TracingHandler(Handler<? super ChatCompletionChunk> delegate) {
      this.delegate = delegate;
    }

    @Override
    public void onNext(ChatCompletionChunk chunk) {
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
