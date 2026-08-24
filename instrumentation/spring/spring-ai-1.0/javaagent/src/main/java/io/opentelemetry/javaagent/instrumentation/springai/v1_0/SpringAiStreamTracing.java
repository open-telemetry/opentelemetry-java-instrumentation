/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.springai.v1_0;

import static io.opentelemetry.javaagent.instrumentation.springai.v1_0.SpringAiSingletons.captureMessageContent;
import static io.opentelemetry.javaagent.instrumentation.springai.v1_0.SpringAiSingletons.captureMessageContentAsSpanAttributes;
import static io.opentelemetry.javaagent.instrumentation.springai.v1_0.SpringAiSingletons.instrumenter;
import static io.opentelemetry.javaagent.instrumentation.springai.v1_0.SpringAiSingletons.messageContentSpanAttributeMaxLength;
import static io.opentelemetry.javaagent.instrumentation.springai.v1_0.SpringAiSingletons.shouldSuppressNestedChatModelInstrumentation;
import static io.opentelemetry.javaagent.instrumentation.springai.v1_0.SpringAiSingletons.suppressNestedChatModelInstrumentation;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.reactor.v3_1.ContextPropagationOperator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nullable;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.content.Media;
import reactor.core.publisher.Flux;
import reactor.util.context.ContextView;

public class SpringAiStreamTracing {
  public static Flux<ChatResponse> wrap(Flux<ChatResponse> source, SpringAiRequest request) {
    return Flux.deferContextual(reactorContext -> start(source, request, reactorContext));
  }

  private static Flux<ChatResponse> start(
      Flux<ChatResponse> source, SpringAiRequest request, ContextView reactorContext) {
    Instrumenter<SpringAiRequest, ChatResponse> chatInstrumenter;
    Context context;
    try {
      chatInstrumenter = instrumenter();
      Context parentContext =
          ContextPropagationOperator.getOpenTelemetryContextFromContextView(
              reactorContext, Context.current());
      if (shouldSuppressNestedChatModelInstrumentation(parentContext)
          || !chatInstrumenter.shouldStart(parentContext, request)) {
        return source;
      }
      context = chatInstrumenter.start(parentContext, request);
    } catch (Throwable ignored) {
      // This method runs outside of Byte Buddy advice when the publisher is subscribed.
      return source;
    }

    try {
      SpringAiMessageAttributes.setInputMessages(context, request);
      SpringAiMessageEvents.emitPromptEvents(context, request);
      AtomicBoolean ended = new AtomicBoolean();
      StreamState state =
          new StreamState(
              captureMessageContent(),
              captureMessageContentAsSpanAttributes(),
              messageContentSpanAttributeMaxLength());
      Flux<ChatResponse> traced =
          source
              // The suppression marker is needed only while subscribing to a deferred delegate.
              // Keeping it in the context propagated to downstream callbacks would suppress a
              // legitimate ChatModel.stream() call made by a downstream operator.
              .contextWrite(
                  contextView ->
                      ContextPropagationOperator.storeOpenTelemetryContext(
                          contextView, suppressNestedChatModelInstrumentation(context)))
              .doOnNext(state::add)
              .doOnError(error -> end(chatInstrumenter, context, request, state, error, ended))
              .doOnComplete(() -> end(chatInstrumenter, context, request, state, null, ended))
              .doOnCancel(() -> end(chatInstrumenter, context, request, state, null, ended));
      return ContextPropagationOperator.runWithContext(traced, context);
    } catch (Throwable ignored) {
      // Do not leak an already-started span if Reactor rejects operator assembly.
      endStartedSpan(chatInstrumenter, context, request);
      return source;
    }
  }

  private static void endStartedSpan(
      Instrumenter<SpringAiRequest, ChatResponse> instrumenter,
      Context context,
      SpringAiRequest request) {
    try {
      instrumenter.end(context, request, null, null);
    } catch (Throwable ignored) {
      // This callback is outside of Byte Buddy advice suppression.
    }
  }

  private static void end(
      Instrumenter<SpringAiRequest, ChatResponse> instrumenter,
      Context context,
      SpringAiRequest request,
      StreamState state,
      @Nullable Throwable error,
      AtomicBoolean ended) {
    if (!ended.compareAndSet(false, true)) {
      return;
    }

    @Nullable ChatResponse response = null;
    @Nullable List<String> streamedContents = null;
    try {
      Snapshot snapshot = state.snapshot();
      response = snapshot.response;
      streamedContents = snapshot.streamedContents;
    } catch (Throwable ignored) {
      // Telemetry state must not affect the instrumented publisher.
    }

    try {
      SpringAiMessageAttributes.setOutputMessages(context, response, streamedContents);
      SpringAiMessageEvents.emitResponseEvents(context, request, response, streamedContents);
    } catch (Throwable ignored) {
      // best effort
    }
    try {
      instrumenter.end(context, request, response, error);
    } catch (Throwable ignored) {
      // This callback is outside of Byte Buddy advice suppression.
    }
  }

  private static final class StreamState {
    private boolean hasResponse;
    private final List<GenerationState> generations = new ArrayList<>();
    @Nullable private String responseId;
    @Nullable private String responseModel;
    @Nullable private Usage usage;
    @Nullable private final List<ContentBuffer> streamedContents;
    private final int contentMaxLength;
    private final boolean captureToolCallArguments;
    private final int toolCallArgumentMaxLength;
    private final boolean captureMedia;

    private StreamState(
        boolean captureMessageContent,
        boolean captureMessageContentAsSpanAttributes,
        int spanAttributeMaxLength) {
      if (captureMessageContent) {
        captureToolCallArguments = true;
        toolCallArgumentMaxLength = -1;
      } else if (captureMessageContentAsSpanAttributes) {
        captureToolCallArguments = true;
        toolCallArgumentMaxLength = spanAttributeMaxLength;
      } else {
        captureToolCallArguments = false;
        toolCallArgumentMaxLength = 0;
      }
      captureMedia = captureMessageContentAsSpanAttributes;

      if (!captureMessageContent && !captureMessageContentAsSpanAttributes) {
        streamedContents = null;
        contentMaxLength = 0;
      } else {
        streamedContents = new ArrayList<>();
        contentMaxLength = captureMessageContent ? -1 : spanAttributeMaxLength;
      }
    }

    private synchronized void add(ChatResponse response) {
      hasResponse = true;
      try {
        List<Generation> generations = response.getResults();
        while (this.generations.size() < generations.size()) {
          this.generations.add(
              new GenerationState(
                  captureToolCallArguments, toolCallArgumentMaxLength, captureMedia));
          if (streamedContents != null) {
            streamedContents.add(new ContentBuffer(contentMaxLength));
          }
        }
        for (int index = 0; index < generations.size(); index++) {
          Generation generation = generations.get(index);
          this.generations.get(index).add(generation);
          if (streamedContents != null) {
            String content = generation.getOutput().getText();
            if (content != null) {
              streamedContents.get(index).append(content);
            }
          }
        }

        ChatResponseMetadata metadata = response.getMetadata();
        if (metadata != null) {
          if (metadata.getId() != null && !metadata.getId().isEmpty()) {
            responseId = metadata.getId();
          }
          if (metadata.getModel() != null && !metadata.getModel().isEmpty()) {
            responseModel = metadata.getModel();
          }
          Usage newUsage = metadata.getUsage();
          if (newUsage != null && !(newUsage instanceof EmptyUsage)) {
            usage = newUsage;
          }
        }
      } catch (Throwable ignored) {
        // Telemetry state must not affect the instrumented publisher.
      }
    }

    private synchronized Snapshot snapshot() {
      if (!hasResponse) {
        return new Snapshot(null, null);
      }

      List<Generation> responseGenerations = new ArrayList<>(generations.size());
      for (GenerationState generation : generations) {
        Generation value = generation.value();
        if (value != null) {
          responseGenerations.add(value);
        }
      }

      ChatResponseMetadata.Builder metadata = ChatResponseMetadata.builder();
      if (responseId != null) {
        metadata.id(responseId);
      }
      if (responseModel != null) {
        metadata.model(responseModel);
      }
      if (usage != null) {
        metadata.usage(usage);
      }
      ChatResponse response = new ChatResponse(responseGenerations, metadata.build());

      if (streamedContents == null) {
        return new Snapshot(response, null);
      }
      List<String> contents = new ArrayList<>(streamedContents.size());
      for (ContentBuffer content : streamedContents) {
        contents.add(content.value());
      }
      return new Snapshot(response, contents);
    }
  }

  private static final class GenerationState {
    private final boolean captureToolCallArguments;
    private final int toolCallArgumentMaxLength;
    @Nullable private Generation generation;
    @Nullable private String finishReason;
    private final List<ToolCallState> toolCalls = new ArrayList<>();
    private final List<Media> media = new ArrayList<>();
    private final boolean captureMedia;

    private GenerationState(
        boolean captureToolCallArguments, int toolCallArgumentMaxLength, boolean captureMedia) {
      this.captureToolCallArguments = captureToolCallArguments;
      this.toolCallArgumentMaxLength = toolCallArgumentMaxLength;
      this.captureMedia = captureMedia;
    }

    private void add(Generation generation) {
      this.generation = generation;
      AssistantMessage output = generation.getOutput();
      addToolCalls(output);
      if (captureMedia) {
        addMedia(output);
      }
      ChatGenerationMetadata metadata = generation.getMetadata();
      if (metadata != null && metadata.getFinishReason() != null) {
        finishReason = metadata.getFinishReason();
      }
    }

    @Nullable
    private Generation value() {
      if (generation == null) {
        return null;
      }

      AssistantMessage output = generation.getOutput();
      if (!toolCalls.isEmpty() || !media.isEmpty()) {
        output = withAccumulatedStructuredParts(output);
      }

      if (finishReason == null) {
        if (output == generation.getOutput()) {
          return generation;
        }
        return new Generation(output, generation.getMetadata());
      }
      ChatGenerationMetadata metadata = generation.getMetadata();
      if (metadata != null && finishReason.equals(metadata.getFinishReason())) {
        if (output == generation.getOutput()) {
          return generation;
        }
        return new Generation(output, metadata);
      }
      return new Generation(
          output, ChatGenerationMetadata.builder().finishReason(finishReason).build());
    }

    private void addToolCalls(AssistantMessage message) {
      List<AssistantMessage.ToolCall> newToolCalls = message.getToolCalls();
      if (newToolCalls == null || newToolCalls.isEmpty()) {
        return;
      }
      for (int index = 0; index < newToolCalls.size(); index++) {
        AssistantMessage.ToolCall toolCall = newToolCalls.get(index);
        toolCallState(toolCall, index).add(toolCall);
      }
    }

    private ToolCallState toolCallState(AssistantMessage.ToolCall toolCall, int index) {
      String id = toolCall.id();
      if (id != null && !id.isEmpty()) {
        for (ToolCallState state : toolCalls) {
          if (id.equals(state.id)) {
            return state;
          }
        }
      }
      if (index < toolCalls.size() && toolCalls.get(index).canMerge(toolCall)) {
        return toolCalls.get(index);
      }

      ToolCallState state = new ToolCallState(captureToolCallArguments, toolCallArgumentMaxLength);
      toolCalls.add(state);
      return state;
    }

    private void addMedia(AssistantMessage message) {
      List<Media> newMedia = message.getMedia();
      if (newMedia == null || newMedia.isEmpty()) {
        return;
      }
      for (Media item : newMedia) {
        if (!media.contains(item)) {
          media.add(item);
        }
      }
    }

    private AssistantMessage withAccumulatedStructuredParts(AssistantMessage message) {
      List<AssistantMessage.ToolCall> aggregatedToolCalls = new ArrayList<>(toolCalls.size());
      for (ToolCallState toolCall : toolCalls) {
        aggregatedToolCalls.add(toolCall.value());
      }
      return assistantMessage(
          message, aggregatedToolCalls, captureMedia && !media.isEmpty() ? media : emptyList());
    }
  }

  private static final class ToolCallState {
    @Nullable private String id;
    @Nullable private String type;
    @Nullable private String name;
    @Nullable private final ContentBuffer arguments;
    private boolean hasArguments;

    private ToolCallState(boolean captureArguments, int argumentMaxLength) {
      arguments = captureArguments ? new ContentBuffer(argumentMaxLength) : null;
    }

    private void add(AssistantMessage.ToolCall toolCall) {
      id = latestNonEmpty(id, toolCall.id());
      type = latestNonEmpty(type, toolCall.type());
      name = latestNonEmpty(name, toolCall.name());
      String newArguments = toolCall.arguments();
      if (arguments != null && newArguments != null && !newArguments.isEmpty()) {
        arguments.append(newArguments);
        hasArguments = true;
      }
    }

    private boolean canMerge(AssistantMessage.ToolCall toolCall) {
      return hasSameOrNoValue(id, toolCall.id())
          && hasSameOrNoValue(type, toolCall.type())
          && hasSameOrNoValue(name, toolCall.name());
    }

    private AssistantMessage.ToolCall value() {
      return new AssistantMessage.ToolCall(
          id, type, name, hasArguments && arguments != null ? arguments.value() : null);
    }

    @Nullable
    private static String latestNonEmpty(@Nullable String current, @Nullable String next) {
      return next == null || next.isEmpty() ? current : next;
    }

    private static boolean hasSameOrNoValue(@Nullable String current, @Nullable String next) {
      if (next == null || next.isEmpty()) {
        return true;
      }
      return current == null || current.isEmpty() || current.equals(next);
    }
  }

  private static AssistantMessage assistantMessage(
      AssistantMessage message, List<AssistantMessage.ToolCall> toolCalls, List<Media> media) {
    try {
      Object builder = AssistantMessage.class.getMethod("builder").invoke(null);
      invokeBuilder(builder, "content", String.class, message.getText());
      invokeBuilder(builder, "properties", Map.class, metadata(message));
      invokeBuilder(builder, "toolCalls", List.class, toolCalls);
      invokeBuilder(builder, "media", List.class, media);
      return (AssistantMessage) builder.getClass().getMethod("build").invoke(builder);
    } catch (NoSuchMethodException e) {
      return assistantMessageWithConstructor(message, toolCalls, media, e);
    } catch (ReflectiveOperationException e) {
      return message;
    }
  }

  private static AssistantMessage assistantMessageWithConstructor(
      AssistantMessage message,
      List<AssistantMessage.ToolCall> toolCalls,
      List<Media> media,
      NoSuchMethodException e) {
    try {
      return AssistantMessage.class
          .getConstructor(String.class, Map.class, List.class, List.class)
          .newInstance(message.getText(), metadata(message), toolCalls, media);
    } catch (ReflectiveOperationException f) {
      e.addSuppressed(f);
      return message;
    }
  }

  private static void invokeBuilder(
      Object builder, String methodName, Class<?> parameterType, Object value)
      throws ReflectiveOperationException {
    builder.getClass().getMethod(methodName, parameterType).invoke(builder, value);
  }

  private static Map<String, Object> metadata(AssistantMessage message) {
    Map<String, Object> metadata = message.getMetadata();
    return metadata == null ? emptyMap() : metadata;
  }

  private static final class ContentBuffer {
    private final StringBuilder content = new StringBuilder();
    private final int maxLength;
    private boolean truncated;

    private ContentBuffer(int maxLength) {
      this.maxLength = maxLength;
    }

    private void append(String value) {
      if (maxLength < 0) {
        content.append(value);
        return;
      }
      if (truncated) {
        return;
      }

      int remaining = maxLength - content.length();
      if (remaining <= 0) {
        truncated = true;
        return;
      }
      int end = SpringAiStringUtil.safeEndIndex(value, remaining);
      content.append(value, 0, end);
      truncated = end < value.length();
    }

    private String value() {
      int length = content.length();
      if (maxLength >= 0
          && length == maxLength
          && length > 0
          && Character.isHighSurrogate(content.charAt(length - 1))) {
        return content.substring(0, length - 1);
      }
      return content.toString();
    }
  }

  private static final class Snapshot {
    @Nullable private final ChatResponse response;
    @Nullable private final List<String> streamedContents;

    private Snapshot(@Nullable ChatResponse response, @Nullable List<String> streamedContents) {
      this.response = response;
      this.streamedContents = streamedContents;
    }
  }

  private SpringAiStreamTracing() {}
}
