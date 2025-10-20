/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.ai.v1_0.chat.client;

import io.opentelemetry.instrumentation.api.genai.MessageCaptureOptions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.AssistantMessage.ToolCall;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.Generation;

final class ChatClientMessageBuffer {
  private static final String TRUNCATE_FLAG = "...[truncated]";
  private final int index;
  private final MessageCaptureOptions messageCaptureOptions;

  @Nullable private String finishReason;

  @Nullable private StringBuilder rawContentBuffer;

  @Nullable private Map<Integer, ToolCallBuffer> toolCalls;

  ChatClientMessageBuffer(int index,
      MessageCaptureOptions messageCaptureOptions) {
    this.index = index;
    this.messageCaptureOptions = messageCaptureOptions;
  }

  Generation toGeneration() {
    List<ToolCall> toolCalls;
    if (this.toolCalls != null) {
      toolCalls = new ArrayList<>(this.toolCalls.size());
      for (Map.Entry<Integer, ToolCallBuffer> entry : this.toolCalls.entrySet()) {
        if (entry.getValue() != null) {
          String arguments;
          if (entry.getValue().function.arguments != null) {
            arguments = entry.getValue().function.arguments.toString();
          } else {
            arguments = "";
          }
          if (entry.getValue().type == null) {
            entry.getValue().type = "function";
          }
          if (entry.getValue().function.name == null) {
            entry.getValue().function.name = "";
          }
          toolCalls.add(new ToolCall(entry.getValue().id, entry.getValue().type,
              entry.getValue().function.name, arguments));
        }
      }
    } else {
      toolCalls = Collections.emptyList();
    }

    String content = "";

    if (this.rawContentBuffer != null) {
      content = this.rawContentBuffer.toString();
    }

    return new Generation(new AssistantMessage(content, Collections.emptyMap(), toolCalls),
        ChatGenerationMetadata.builder().finishReason(this.finishReason).build());
  }

  void append(Generation generation) {
    AssistantMessage message = generation.getOutput();
    if (message != null) {
      if (this.messageCaptureOptions.captureMessageContent()) {
        if (message.getText() != null) {
          if (this.rawContentBuffer == null) {
            this.rawContentBuffer = new StringBuilder();
          }

          String deltaContent = message.getText();
          if (this.rawContentBuffer.length() < this.messageCaptureOptions.maxMessageContentLength()) {
            if (this.rawContentBuffer.length() + deltaContent.length() >= this.messageCaptureOptions.maxMessageContentLength()) {
              deltaContent = deltaContent.substring(0, this.messageCaptureOptions.maxMessageContentLength() - this.rawContentBuffer.length());
              this.rawContentBuffer.append(deltaContent).append(TRUNCATE_FLAG);
            } else {
              this.rawContentBuffer.append(deltaContent);
            }
          }
        }
      }

      if (message.hasToolCalls()) {
        if (this.toolCalls == null) {
          this.toolCalls = new HashMap<>();
        }

        for (int i = 0; i < message.getToolCalls().size(); i++) {
          ToolCall toolCall = message.getToolCalls().get(i);
          ToolCallBuffer buffer =
              this.toolCalls.computeIfAbsent(
                  i, unused -> new ToolCallBuffer(toolCall.id()));

          buffer.type = toolCall.type();
          buffer.function.name = toolCall.name();
          if (this.messageCaptureOptions.captureMessageContent()) {
            if (buffer.function.arguments == null) {
              buffer.function.arguments = new StringBuilder();
            }
            buffer.function.arguments.append(toolCall.arguments());
          }
        }
      }
    }

    ChatGenerationMetadata metadata = generation.getMetadata();
    if (metadata != null && metadata.getFinishReason() != null && !metadata.getFinishReason().isEmpty()) {
      this.finishReason = metadata.getFinishReason();
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
