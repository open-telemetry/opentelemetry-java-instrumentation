/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.ai.openai.v1_0;

import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionFinishReason;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionMessage;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionMessage.ChatCompletionFunction;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionMessage.Role;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionMessage.ToolCall;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletion.Choice;
import io.opentelemetry.instrumentation.api.genai.MessageCaptureOptions;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

final class ChatModelMessageBuffer {
  private static final String TRUNCATE_FLAG = "...[truncated]";
  private final int index;
  private final MessageCaptureOptions messageCaptureOptions;

  @Nullable private ChatCompletionFinishReason finishReason;

  @Nullable private StringBuilder rawContent;

  @Nullable private Role role;

  @Nullable private String name;

  @Nullable private String toolCallId;

  @Nullable private Map<Integer, ToolCallBuffer> toolCalls;

  ChatModelMessageBuffer(int index, MessageCaptureOptions messageCaptureOptions) {
    this.index = index;
    this.messageCaptureOptions = messageCaptureOptions;
  }

  Choice toChoice() {
    List<ToolCall> toolCalls = null;
    if (this.toolCalls != null) {
      toolCalls = new ArrayList<>(this.toolCalls.size());
      for (Map.Entry<Integer, ToolCallBuffer> entry : this.toolCalls.entrySet()) {
        if (entry.getValue() != null) {
          String arguments = null;
          if (entry.getValue().function.arguments != null) {
            arguments = entry.getValue().function.arguments.toString();
          }
          toolCalls.add(new ToolCall(entry.getValue().id, entry.getValue().type,
              new ChatCompletionFunction(entry.getValue().function.name, arguments)));
        }
      }
    }

    String content = "";
    // Type of content is String for OpenAI
    if (rawContent != null) {
      content = rawContent.toString();
    }

    return new Choice(
        finishReason,
        index,
        new ChatCompletionMessage(content, role, name, toolCallId, toolCalls, null, null, null),
        null);
  }

  void append(Choice choice) {
    if (choice.message() != null) {
      if (this.messageCaptureOptions.captureMessageContent()) {
        // Type of content is String for OpenAI
        if (choice.message().rawContent() instanceof String) {
          if (this.rawContent == null) {
            this.rawContent = new StringBuilder();
          }

          String deltaContent = (String) choice.message().rawContent();
          if (this.rawContent.length() < this.messageCaptureOptions.maxMessageContentLength()) {
            if (this.rawContent.length() + deltaContent.length() >= this.messageCaptureOptions.maxMessageContentLength() ) {
              deltaContent = deltaContent.substring(0, this.messageCaptureOptions.maxMessageContentLength() - this.rawContent.length());
              this.rawContent.append(deltaContent).append(TRUNCATE_FLAG);
            } else {
              this.rawContent.append(deltaContent);
            }
          }
        }
      }

      if (choice.message().toolCalls() != null) {
        if (this.toolCalls == null) {
          this.toolCalls = new HashMap<>();
        }

        for (int i = 0; i < choice.message().toolCalls().size(); i++) {
          ToolCall toolCall = choice.message().toolCalls().get(i);
          ToolCallBuffer buffer =
              this.toolCalls.computeIfAbsent(
                  i, unused -> new ToolCallBuffer(toolCall.id()));
          if (toolCall.type() != null) {
            buffer.type = toolCall.type();
          }

          if (toolCall.function() != null) {
            if (toolCall.function().name() != null) {
              buffer.function.name = toolCall.function().name();
            }
            if (this.messageCaptureOptions.captureMessageContent() && toolCall.function().arguments() != null) {
              if (buffer.function.arguments == null) {
                buffer.function.arguments = new StringBuilder();
              }
              buffer.function.arguments.append(toolCall.function().arguments());
            }
          }
        }
      }

      if (choice.message().role() != null) {
        this.role = choice.message().role();
      }
      if (choice.message().name() != null) {
        this.name = choice.message().name();
      }
      if (choice.message().toolCallId() != null) {
        this.toolCallId = choice.message().toolCallId();
      }
    }

    if (choice.finishReason() != null) {
      this.finishReason = choice.finishReason();
    }
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
