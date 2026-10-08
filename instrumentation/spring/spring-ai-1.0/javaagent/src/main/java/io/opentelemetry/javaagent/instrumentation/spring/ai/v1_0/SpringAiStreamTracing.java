/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.ai.v1_0;

import static io.opentelemetry.javaagent.instrumentation.spring.ai.v1_0.SpringAiSingletons.captureMessageContent;
import static io.opentelemetry.javaagent.instrumentation.spring.ai.v1_0.SpringAiSingletons.instrumenter;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Objects.requireNonNull;
import static java.util.logging.Level.FINE;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.reactor.v3_1.ContextPropagationOperator;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
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

class SpringAiStreamTracing {
  private static final Logger logger = Logger.getLogger(SpringAiStreamTracing.class.getName());

  static Flux<ChatResponse> wrap(Flux<ChatResponse> source, SpringAiRequest request) {
    return Flux.deferContextual(reactorContext -> start(source, request, reactorContext));
  }

  private static Flux<ChatResponse> start(
      Flux<ChatResponse> source, SpringAiRequest request, ContextView reactorContext) {
    Instrumenter<SpringAiRequest, SpringAiResponse> chatInstrumenter;
    Context parentContext;
    Context context;
    try {
      chatInstrumenter = instrumenter();
      parentContext =
          requireNonNull(
              ContextPropagationOperator.getOpenTelemetryContextFromContextView(
                  reactorContext, Context.current()));
      if (!chatInstrumenter.shouldStart(parentContext, request)) {
        return source;
      }
      context = chatInstrumenter.start(parentContext, request);
    } catch (Throwable t) {
      // This method runs outside of Byte Buddy advice when the publisher is subscribed.
      logger.log(FINE, "Failed to start Spring AI stream instrumentation", t);
      return source;
    }

    try {
      SpringAiMessageEvents.emitPromptEvents(context, request);
    } catch (Throwable t) {
      logger.log(FINE, "Failed to emit Spring AI prompt events", t);
    }
    AtomicBoolean ended = new AtomicBoolean();
    StreamState state = new StreamState(captureMessageContent());
    Flux<ChatResponse> traced =
        source
            // Suppress nested GenAI operations in the source, including deferred delegates.
            .contextWrite(
                contextView ->
                    ContextPropagationOperator.storeOpenTelemetryContext(contextView, context))
            .doOnNext(state::add)
            .doOnError(error -> end(chatInstrumenter, context, request, state, error, ended))
            .doOnComplete(() -> end(chatInstrumenter, context, request, state, null, ended))
            .doOnCancel(() -> end(chatInstrumenter, context, request, state, null, ended));
    // Downstream callbacks run with the parent context, matching other client instrumentations.
    return ContextPropagationOperator.runWithContext(traced, parentContext);
  }

  private static void end(
      Instrumenter<SpringAiRequest, SpringAiResponse> instrumenter,
      Context context,
      SpringAiRequest request,
      StreamState state,
      @Nullable Throwable error,
      AtomicBoolean ended) {
    if (!ended.compareAndSet(false, true)) {
      return;
    }

    SpringAiResponse response = null;
    try {
      response = state.snapshot();
    } catch (Throwable ignored) {
      // Telemetry state must not affect the instrumented publisher.
    }

    try {
      SpringAiMessageEvents.emitResponseEvents(
          context,
          request,
          response == null ? null : response.response(),
          response == null ? null : response.streamedContents());
    } catch (Throwable ignored) {
      // best effort
    }
    try {
      instrumenter.end(context, request, response, error);
    } catch (Throwable t) {
      Span.fromContext(context).end();
      logger.log(FINE, "Failed to end Spring AI stream instrumentation", t);
    }
  }

  private static final class StreamState {
    private boolean hasResponse;
    private final Map<Integer, GenerationState> generations = new TreeMap<>();
    @Nullable private String responseId;
    @Nullable private String responseModel;
    @Nullable private Usage usage;
    @Nullable private final Map<Integer, ContentBuffer> streamedContents;
    private final boolean captureToolCallArguments;

    private StreamState(boolean captureMessageContent) {
      captureToolCallArguments = captureMessageContent;
      streamedContents = captureMessageContent ? new TreeMap<>() : null;
    }

    private synchronized void add(ChatResponse response) {
      try {
        addInternal(response);
      } catch (Throwable ignored) {
        // Telemetry state must not affect the instrumented publisher.
      }
    }

    private void addInternal(ChatResponse response) {
      hasResponse = true;
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

      List<Generation> generations = response.getResults();
      for (int position = 0; position < generations.size(); position++) {
        Generation generation = generations.get(position);
        int index = SpringAiMessageEvents.choiceIndex(generation, position);
        GenerationState generationState = this.generations.get(index);
        if (generationState == null) {
          generationState = new GenerationState(captureToolCallArguments);
          this.generations.put(index, generationState);
        }
        generationState.add(generation);
        if (streamedContents != null) {
          ContentBuffer contentBuffer = streamedContents.get(index);
          if (contentBuffer == null) {
            contentBuffer = new ContentBuffer(-1);
            streamedContents.put(index, contentBuffer);
          }
          String content = generation.getOutput().getText();
          if (content != null) {
            contentBuffer.append(content);
          }
        }
      }
    }

    @Nullable
    private synchronized SpringAiResponse snapshot() {
      if (!hasResponse) {
        return null;
      }

      List<Generation> responseGenerations = new ArrayList<>(generations.size());
      List<String> contents = streamedContents == null ? null : new ArrayList<>(generations.size());
      for (Map.Entry<Integer, GenerationState> entry : generations.entrySet()) {
        Generation value = entry.getValue().value();
        if (value != null) {
          responseGenerations.add(value);
          if (contents != null && streamedContents != null) {
            ContentBuffer content = streamedContents.get(entry.getKey());
            contents.add(content == null ? "" : content.value());
          }
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

      return new SpringAiResponse(response, contents);
    }
  }

  private static final class GenerationState {
    private final boolean captureToolCallArguments;
    @Nullable private Generation generation;
    @Nullable private String finishReason;
    private final List<ToolCallState> toolCalls = new ArrayList<>();

    private GenerationState(boolean captureToolCallArguments) {
      this.captureToolCallArguments = captureToolCallArguments;
    }

    private void add(Generation generation) {
      this.generation = generation;
      AssistantMessage output = generation.getOutput();
      addToolCalls(output);
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
      if (!toolCalls.isEmpty()) {
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

      ToolCallState state = new ToolCallState(captureToolCallArguments);
      toolCalls.add(state);
      return state;
    }

    private AssistantMessage withAccumulatedStructuredParts(AssistantMessage message) {
      List<AssistantMessage.ToolCall> aggregatedToolCalls = new ArrayList<>(toolCalls.size());
      for (ToolCallState toolCall : toolCalls) {
        aggregatedToolCalls.add(toolCall.value());
      }
      return assistantMessage(message, aggregatedToolCalls, emptyList());
    }
  }

  private static final class ToolCallState {
    @Nullable private String id;
    @Nullable private String type;
    @Nullable private String name;
    @Nullable private final ContentBuffer arguments;
    private boolean hasArguments;

    private ToolCallState(boolean captureArguments) {
      arguments = captureArguments ? new ContentBuffer(-1) : null;
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
    AssistantMessage reconstructed = AssistantMessageAccessors.create(message, toolCalls, media);
    return reconstructed == null ? message : reconstructed;
  }

  private static final class AssistantMessageAccessors {
    @Nullable private static final BuilderAccessors builder;
    @Nullable private static final Constructor<AssistantMessage> constructor;

    static {
      BuilderAccessors builderAccessors = null;
      Constructor<AssistantMessage> messageConstructor = null;

      try {
        Method builderMethod = AssistantMessage.class.getMethod("builder");
        Class<?> builderClass = builderMethod.getReturnType();
        builderAccessors =
            new BuilderAccessors(
                builderMethod,
                builderClass.getMethod("content", String.class),
                builderClass.getMethod("properties", Map.class),
                builderClass.getMethod("toolCalls", List.class),
                builderClass.getMethod("media", List.class),
                builderClass.getMethod("build"));
      } catch (ReflectiveOperationException ignored) {
        try {
          messageConstructor =
              AssistantMessage.class.getConstructor(
                  String.class, Map.class, List.class, List.class);
        } catch (ReflectiveOperationException ignoredConstructor) {
          // No supported reconstruction API is available.
        }
      }

      builder = builderAccessors;
      constructor = messageConstructor;
    }

    @Nullable
    private static AssistantMessage create(
        AssistantMessage message, List<AssistantMessage.ToolCall> toolCalls, List<Media> media) {
      if (builder != null) {
        try {
          Object builderInstance = builder.builderMethod.invoke(null);
          builder.contentMethod.invoke(builderInstance, message.getText());
          builder.propertiesMethod.invoke(builderInstance, metadata(message));
          builder.toolCallsMethod.invoke(builderInstance, toolCalls);
          builder.mediaMethod.invoke(builderInstance, media);
          return (AssistantMessage) builder.buildMethod.invoke(builderInstance);
        } catch (ReflectiveOperationException ignored) {
          return null;
        }
      }

      if (constructor != null) {
        try {
          return constructor.newInstance(message.getText(), metadata(message), toolCalls, media);
        } catch (ReflectiveOperationException ignored) {
          return null;
        }
      }
      return null;
    }

    private static final class BuilderAccessors {
      private final Method builderMethod;
      private final Method contentMethod;
      private final Method propertiesMethod;
      private final Method toolCallsMethod;
      private final Method mediaMethod;
      private final Method buildMethod;

      private BuilderAccessors(
          Method builderMethod,
          Method contentMethod,
          Method propertiesMethod,
          Method toolCallsMethod,
          Method mediaMethod,
          Method buildMethod) {
        this.builderMethod = builderMethod;
        this.contentMethod = contentMethod;
        this.propertiesMethod = propertiesMethod;
        this.toolCallsMethod = toolCallsMethod;
        this.mediaMethod = mediaMethod;
        this.buildMethod = buildMethod;
      }
    }
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
      int end = safeEndIndex(value, remaining);
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

    private static int safeEndIndex(String value, int maxLength) {
      int end = Math.min(value.length(), Math.max(0, maxLength));
      if (end < value.length()
          && end > 0
          && Character.isHighSurrogate(value.charAt(end - 1))
          && Character.isLowSurrogate(value.charAt(end))) {
        end--;
      }
      return end;
    }
  }

  private SpringAiStreamTracing() {}
}
