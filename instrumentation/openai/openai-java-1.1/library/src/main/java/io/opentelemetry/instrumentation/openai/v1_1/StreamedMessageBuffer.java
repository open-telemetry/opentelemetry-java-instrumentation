/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.openai.v1_1;

import com.openai.core.JsonField;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionChunk;
import com.openai.models.chat.completions.ChatCompletionMessage;
import io.opentelemetry.api.common.Value;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

final class StreamedMessageBuffer {
  private final long index;
  private final boolean captureMessageContent;

  @Nullable String finishReason;

  @Nullable private StringBuilder message;
  @Nullable private Map<Long, ToolCallBuffer> toolCalls;

  StreamedMessageBuffer(long index, boolean captureMessageContent) {
    this.index = index;
    this.captureMessageContent = captureMessageContent;
  }

  ChatCompletion.Choice toChoice() {
    ChatCompletion.Choice.Builder choice =
        ChatCompletion.Choice.builder().index(index).logprobs(Optional.empty());
    if (finishReason != null) {
      choice.finishReason(ChatCompletion.Choice.FinishReason.of(finishReason));
    } else {
      // Can't happen in practice, mostly to satisfy null check
      choice.finishReason(JsonField.ofNullable(null));
    }
    if (message != null) {
      choice.message(
          ChatCompletionMessage.builder()
              .content(message.toString())
              .refusal(Optional.empty())
              .build());
    } else {
      choice.message(JsonField.ofNullable(null));
    }
    return choice.build();
  }

  Value<?> toEventBody() {
    Map<String, Value<?>> body = new HashMap<>();
    if (message != null) {
      body.put("content", Value.of(message.toString()));
    }
    if (toolCalls != null) {
      List<Value<?>> toolCallsJson =
          toolCalls.values().stream()
              .map(StreamedMessageBuffer::buildToolCallEventObject)
              .collect(Collectors.toList());
      body.put("tool_calls", Value.of(toolCallsJson));
    }
    return Value.of(body);
  }

  void append(ChatCompletionChunk.Choice.Delta delta) {
    if (captureMessageContent) {
      if (delta.content().isPresent()) {
        if (message == null) {
          message = new StringBuilder();
        }
        message.append(delta.content().get());
      }
    }

    if (delta.toolCalls().isPresent()) {
      if (toolCalls == null) {
        toolCalls = new HashMap<>();
      }

      for (ChatCompletionChunk.Choice.Delta.ToolCall toolCall : delta.toolCalls().get()) {
        ToolCallBuffer buffer =
            toolCalls.computeIfAbsent(
                toolCall.index(), unused -> new ToolCallBuffer(toolCall.id().orElse("")));
        toolCall.type().ifPresent(type -> buffer.type = type.toString());
        toolCall
            .function()
            .ifPresent(
                function -> {
                  function.name().ifPresent(name -> buffer.function.name = name);
                  if (captureMessageContent) {
                    function
                        .arguments()
                        .ifPresent(
                            args -> {
                              if (buffer.function.arguments == null) {
                                buffer.function.arguments = new StringBuilder();
                              }
                              buffer.function.arguments.append(args);
                            });
                  }
                });
      }
    }
  }

  private static Value<?> buildToolCallEventObject(ToolCallBuffer call) {
    Map<String, Value<?>> result = new HashMap<>();
    result.put("id", Value.of(call.id));
    if (call.type != null) {
      result.put("type", Value.of(call.type));
    }

    Map<String, Value<?>> function = new HashMap<>();
    if (call.function.name != null) {
      function.put("name", Value.of(call.function.name));
    }
    if (call.function.arguments != null) {
      function.put("arguments", Value.of(call.function.arguments.toString()));
    }
    result.put("function", Value.of(function));

    return Value.of(result);
  }

  private static class FunctionBuffer {
    @Nullable String name;
    @Nullable StringBuilder arguments;
  }

  private static class ToolCallBuffer {
    final String id;
    final FunctionBuffer function = new FunctionBuffer();
    @Nullable String type;

    ToolCallBuffer(String id) {
      this.id = id;
    }
  }
}
