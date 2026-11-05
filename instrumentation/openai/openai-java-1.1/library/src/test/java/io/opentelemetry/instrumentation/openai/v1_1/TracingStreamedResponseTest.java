/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.openai.v1_1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openai.core.http.AsyncStreamResponse;
import com.openai.core.http.StreamResponse;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;

class TracingStreamedResponseTest {

  private static final class FailingListener implements ChunkListener<String> {
    @Override
    public void onChunk(String chunk) {}

    @Override
    public void endSpan(@Nullable Throwable error) {
      throw new IllegalStateException("ending the span failed");
    }
  }

  @Test
  void closesDelegateWhenEndingSpanFails() {
    AtomicBoolean closed = new AtomicBoolean();
    StreamResponse<String> delegate =
        new StreamResponse<String>() {
          @Override
          public Stream<String> stream() {
            return Stream.empty();
          }

          @Override
          public void close() {
            closed.set(true);
          }
        };

    TracingStreamedResponse<String> response =
        new TracingStreamedResponse<>(delegate, new FailingListener());

    assertThatThrownBy(response::close).isInstanceOf(IllegalStateException.class);
    assertThat(closed).isTrue();
  }

  @Test
  void closesAsyncDelegateWhenEndingSpanFails() {
    AtomicBoolean closed = new AtomicBoolean();
    AsyncStreamResponse<String> delegate =
        new AsyncStreamResponse<String>() {
          @Override
          public AsyncStreamResponse<String> subscribe(Handler<? super String> handler) {
            return this;
          }

          @Override
          public AsyncStreamResponse<String> subscribe(
              Handler<? super String> handler, Executor executor) {
            return this;
          }

          @Override
          public CompletableFuture<Void> onCompleteFuture() {
            return new CompletableFuture<>();
          }

          @Override
          public void close() {
            closed.set(true);
          }
        };

    TracingAsyncStreamedResponse<String> response =
        new TracingAsyncStreamedResponse<>(delegate, new FailingListener());

    assertThatThrownBy(response::close).isInstanceOf(IllegalStateException.class);
    assertThat(closed).isTrue();
  }

  @Test
  void closesDelegateWhenEndingSpanSucceeds() {
    AtomicBoolean closed = new AtomicBoolean();
    StreamResponse<String> delegate =
        new StreamResponse<String>() {
          @Override
          public Stream<String> stream() {
            return Stream.empty();
          }

          @Override
          public void close() {
            closed.set(true);
          }
        };
    AtomicBoolean ended = new AtomicBoolean();
    ChunkListener<String> listener =
        new ChunkListener<String>() {
          @Override
          public void onChunk(String chunk) {}

          @Override
          public void endSpan(@Nullable Throwable error) {
            ended.set(true);
          }
        };

    new TracingStreamedResponse<>(delegate, listener).close();

    assertThat(ended).isTrue();
    assertThat(closed).isTrue();
  }
}
