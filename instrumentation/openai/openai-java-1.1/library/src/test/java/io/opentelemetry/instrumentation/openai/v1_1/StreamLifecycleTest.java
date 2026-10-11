/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.openai.v1_1;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.openai.core.http.AsyncStreamResponse;
import com.openai.core.http.StreamResponse;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionChunk;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class StreamLifecycleTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  private static final AttributeKey<String> EVENT_NAME = AttributeKey.stringKey("event.name");
  private static final AttributeKey<String> RESPONSE_ID = AttributeKey.stringKey("response.id");
  private static final AttributeKey<List<String>> RESPONSE_CONTENT =
      AttributeKey.stringArrayKey("response.content");
  private static final AttributeKey<List<String>> RESPONSE_FINISH_REASONS =
      AttributeKey.stringArrayKey("response.finish_reasons");

  private static final ChatCompletionRequest request =
      ChatCompletionRequest.createStreaming(
          ChatCompletionCreateParams.builder().model("gpt-4o-mini").addUserMessage("Hi").build());

  private final AtomicInteger endCount = new AtomicInteger();

  // mockito-inline can mock the final Kotlin accessors
  @SuppressWarnings("CannotMockMethod")
  @Test
  void closeOverlappingChunkIncludesChunkInResponse() throws Exception {
    StreamListener listener = createListener(recordingExtractor());
    FakeAsyncStreamResponse delegate = new FakeAsyncStreamResponse();
    RecordingHandler handler = new RecordingHandler();
    TracingAsyncStreamedResponse response = new TracingAsyncStreamedResponse(delegate, listener);
    response.subscribe(handler);

    ChatCompletionChunk chunk = chunk("Hello", true);
    CountDownLatch chunkProcessingStarted = new CountDownLatch(1);
    CountDownLatch finishChunkProcessing = new CountDownLatch(1);
    ChatCompletionChunk blockingChunk = mock(ChatCompletionChunk.class);
    when(blockingChunk.model())
        .thenAnswer(
            invocation -> {
              chunkProcessingStarted.countDown();
              finishChunkProcessing.await();
              return chunk.model();
            });
    when(blockingChunk.id()).thenReturn(chunk.id());
    when(blockingChunk.usage()).thenReturn(Optional.empty());
    when(blockingChunk.choices()).thenReturn(chunk.choices());

    Thread chunkThread = new Thread(() -> delegate.handler().onNext(blockingChunk));
    Thread closeThread = new Thread(response::close);
    try {
      chunkThread.start();
      assertThat(chunkProcessingStarted.await(10, SECONDS)).isTrue();

      closeThread.start();
      // close must wait for the chunk that is being processed
      await()
          .atMost(Duration.ofSeconds(10))
          .until(() -> closeThread.getState() == Thread.State.BLOCKED);
      assertThat(delegate.closed).isFalse();
    } finally {
      finishChunkProcessing.countDown();
    }
    chunkThread.join(SECONDS.toMillis(10));
    closeThread.join(SECONDS.toMillis(10));
    assertThat(chunkThread.isAlive()).isFalse();
    assertThat(closeThread.isAlive()).isFalse();

    // stream completion reported after the user close does not end the span again
    delegate.handler().onComplete(Optional.of(new IllegalStateException("stream closed")));

    assertThat(delegate.closed).isTrue();
    assertThat(handler.chunks).containsExactly(blockingChunk);
    assertThat(handler.completions).isEqualTo(1);
    assertThat(endCount).hasValue(1);
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("chat")
                        .hasNoParent()
                        .hasStatus(StatusData.unset())
                        .hasAttributesSatisfyingExactly(
                            equalTo(RESPONSE_ID, "chatcmpl-1"),
                            equalTo(RESPONSE_CONTENT, singletonList("Hello")),
                            equalTo(RESPONSE_FINISH_REASONS, singletonList("stop")))));
    assertThat(choiceEvents()).hasSize(1);
  }

  @Test
  void completionEndsSpanOnce() {
    StreamListener listener = createListener(recordingExtractor());
    FakeAsyncStreamResponse delegate = new FakeAsyncStreamResponse();
    RecordingHandler handler = new RecordingHandler();
    TracingAsyncStreamedResponse response = new TracingAsyncStreamedResponse(delegate, listener);
    response.subscribe(handler, Runnable::run);

    ChatCompletionChunk chunk = chunk("Hello", true);
    delegate.handler().onNext(chunk);
    IllegalStateException error = new IllegalStateException("failure");
    delegate.handler().onComplete(Optional.of(error));
    // chunks and close after completion do not change the recorded response
    delegate.handler().onNext(chunk("ignored", true));
    response.close();

    assertThat(delegate.closed).isTrue();
    assertThat(handler.chunks).hasSize(2);
    assertThat(handler.completions).isEqualTo(1);
    assertThat(endCount).hasValue(1);
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("chat")
                        .hasNoParent()
                        .hasStatus(StatusData.error())
                        .hasAttributesSatisfyingExactly(
                            equalTo(RESPONSE_ID, "chatcmpl-1"),
                            equalTo(RESPONSE_CONTENT, singletonList("Hello")),
                            equalTo(RESPONSE_FINISH_REASONS, singletonList("stop")))));
    assertThat(choiceEvents()).hasSize(1);
  }

  @Test
  void asyncCloseClosesDelegateWhenTelemetryFails() {
    StreamListener listener = createListener(failingExtractor());
    FakeAsyncStreamResponse delegate = new FakeAsyncStreamResponse();
    TracingAsyncStreamedResponse response = new TracingAsyncStreamedResponse(delegate, listener);

    assertThatThrownBy(response::close).hasMessage("telemetry failure");
    assertThat(delegate.closed).isTrue();
  }

  @Test
  void asyncCompletionNotifiesHandlerWhenTelemetryFails() {
    StreamListener listener = createListener(failingExtractor());
    FakeAsyncStreamResponse delegate = new FakeAsyncStreamResponse();
    RecordingHandler handler = new RecordingHandler();
    new TracingAsyncStreamedResponse(delegate, listener).subscribe(handler);

    assertThatThrownBy(() -> delegate.handler().onComplete(Optional.empty()))
        .hasMessage("telemetry failure");
    assertThat(handler.completions).isEqualTo(1);
  }

  @Test
  void syncCloseClosesDelegateWhenTelemetryFails() {
    StreamListener listener = createListener(failingExtractor());
    FakeStreamResponse delegate = new FakeStreamResponse();
    TracingStreamedResponse response = new TracingStreamedResponse(delegate, listener);

    assertThatThrownBy(response::close).hasMessage("telemetry failure");
    assertThat(delegate.closed).isTrue();
  }

  private static List<LogRecordData> choiceEvents() {
    return testing.logRecords().stream()
        .filter(log -> "gen_ai.choice".equals(log.getAttributes().get(EVENT_NAME)))
        .collect(toList());
  }

  private static StreamListener createListener(
      AttributesExtractor<ChatCompletionRequest, ChatCompletion> extractor) {
    Instrumenter<ChatCompletionRequest, ChatCompletion> instrumenter =
        Instrumenter.<ChatCompletionRequest, ChatCompletion>builder(
                testing.getOpenTelemetry(), "test", unused -> "chat")
            .addAttributesExtractor(extractor)
            .buildInstrumenter();
    Context context = instrumenter.start(Context.root(), request);
    return new StreamListener(
        context,
        request,
        instrumenter,
        testing.getOpenTelemetry().getLogsBridge().get("test"),
        true,
        true);
  }

  private AttributesExtractor<ChatCompletionRequest, ChatCompletion> recordingExtractor() {
    return new AttributesExtractor<ChatCompletionRequest, ChatCompletion>() {
      @Override
      public void onStart(
          AttributesBuilder attributes, Context parentContext, ChatCompletionRequest request) {}

      @Override
      public void onEnd(
          AttributesBuilder attributes,
          Context context,
          ChatCompletionRequest request,
          @Nullable ChatCompletion response,
          @Nullable Throwable error) {
        endCount.incrementAndGet();
        if (response == null) {
          return;
        }
        List<String> content = new ArrayList<>();
        List<String> finishReasons = new ArrayList<>();
        for (ChatCompletion.Choice choice : response.choices()) {
          content.add(choice.message().content().orElse(""));
          finishReasons.add(choice.finishReason().toString());
        }
        attributes.put(RESPONSE_ID, response.id());
        attributes.put(RESPONSE_CONTENT, content);
        attributes.put(RESPONSE_FINISH_REASONS, finishReasons);
      }
    };
  }

  private static AttributesExtractor<ChatCompletionRequest, ChatCompletion> failingExtractor() {
    return new AttributesExtractor<ChatCompletionRequest, ChatCompletion>() {
      @Override
      public void onStart(
          AttributesBuilder attributes, Context parentContext, ChatCompletionRequest request) {}

      @Override
      public void onEnd(
          AttributesBuilder attributes,
          Context context,
          ChatCompletionRequest request,
          @Nullable ChatCompletion response,
          @Nullable Throwable error) {
        throw new IllegalStateException("telemetry failure");
      }
    };
  }

  private static ChatCompletionChunk chunk(String content, boolean finished) {
    return ChatCompletionChunk.builder()
        .id("chatcmpl-1")
        .created(0)
        .model("gpt-4o-mini")
        .choices(
            singletonList(
                ChatCompletionChunk.Choice.builder()
                    .index(0)
                    .delta(ChatCompletionChunk.Choice.Delta.builder().content(content).build())
                    .finishReason(
                        finished
                            ? Optional.of(ChatCompletionChunk.Choice.FinishReason.STOP)
                            : Optional.empty())
                    .build()))
        .build();
  }

  private static class FakeAsyncStreamResponse implements AsyncStreamResponse<ChatCompletionChunk> {
    @Nullable private Handler<? super ChatCompletionChunk> handler;
    private volatile boolean closed;

    Handler<? super ChatCompletionChunk> handler() {
      assertThat(handler).isNotNull();
      return handler;
    }

    @Override
    public AsyncStreamResponse<ChatCompletionChunk> subscribe(
        Handler<? super ChatCompletionChunk> handler) {
      this.handler = handler;
      return this;
    }

    @Override
    public AsyncStreamResponse<ChatCompletionChunk> subscribe(
        Handler<? super ChatCompletionChunk> handler, Executor executor) {
      return subscribe(handler);
    }

    @Override
    public CompletableFuture<Void> onCompleteFuture() {
      return new CompletableFuture<>();
    }

    @Override
    public void close() {
      closed = true;
    }
  }

  private static class FakeStreamResponse implements StreamResponse<ChatCompletionChunk> {
    private boolean closed;

    @Override
    public Stream<ChatCompletionChunk> stream() {
      return Stream.empty();
    }

    @Override
    public void close() {
      closed = true;
    }
  }

  private static class RecordingHandler
      implements AsyncStreamResponse.Handler<ChatCompletionChunk> {
    private final List<ChatCompletionChunk> chunks = new ArrayList<>();
    private int completions;

    @Override
    public void onNext(ChatCompletionChunk chunk) {
      chunks.add(chunk);
    }

    @Override
    public void onComplete(Optional<Throwable> error) {
      completions++;
    }
  }
}
