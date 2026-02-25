/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.ai.openai.v1_0;

import io.opentelemetry.instrumentation.api.genai.MessageCaptureOptions;
import io.opentelemetry.instrumentation.api.genai.messages.InputMessage;
import io.opentelemetry.instrumentation.api.genai.messages.InputMessages;
import io.opentelemetry.instrumentation.api.genai.messages.MessagePart;
import io.opentelemetry.instrumentation.api.genai.messages.OutputMessage;
import io.opentelemetry.instrumentation.api.genai.messages.OutputMessages;
import io.opentelemetry.instrumentation.api.genai.messages.Role;
import io.opentelemetry.instrumentation.api.genai.messages.SystemInstructions;
import io.opentelemetry.instrumentation.api.genai.messages.TextPart;
import io.opentelemetry.instrumentation.api.genai.messages.ToolCallRequestPart;
import io.opentelemetry.instrumentation.api.genai.messages.ToolCallResponsePart;
import io.opentelemetry.instrumentation.api.genai.messages.ToolDefinition;
import io.opentelemetry.instrumentation.api.genai.messages.ToolDefinitions;
import io.opentelemetry.instrumentation.api.instrumenter.genai.GenAiMessagesProvider;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletion;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletion.Choice;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionMessage;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionMessage.ToolCall;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionRequest;

public final class ChatModelMessagesProvider
    implements GenAiMessagesProvider<ChatCompletionRequest, ChatCompletion> {

  private static final String TRUNCATE_FLAG = "...[truncated]";

  private final MessageCaptureOptions messageCaptureOptions;

  ChatModelMessagesProvider(MessageCaptureOptions messageCaptureOptions) {
    this.messageCaptureOptions = messageCaptureOptions;
  }

  public static ChatModelMessagesProvider create(MessageCaptureOptions messageCaptureOptions) {
    return new ChatModelMessagesProvider(messageCaptureOptions);
  }

  @Nullable
  @Override
  public InputMessages inputMessages(
      ChatCompletionRequest request, @Nullable ChatCompletion response) {
    if (!messageCaptureOptions.captureMessageContent() || request.messages() == null) {
      return null;
    }

    InputMessages inputMessages = InputMessages.create();
    for (ChatCompletionMessage msg : request.messages()) {

      if (msg.role() == ChatCompletionMessage.Role.SYSTEM) {
        inputMessages.append(
            InputMessage.create(Role.SYSTEM, contentToMessageParts(msg.rawContent())));
      } else if (msg.role() == ChatCompletionMessage.Role.USER) {
        inputMessages.append(
            InputMessage.create(Role.USER, contentToMessageParts(msg.rawContent())));
      } else if (msg.role() == ChatCompletionMessage.Role.ASSISTANT) {
        List<MessagePart> messageParts = new ArrayList<>();

        List<MessagePart> contentParts = contentToMessagePartsOrNull(msg.rawContent());
        if (contentParts != null) {
          messageParts.addAll(contentParts);
        }

        List<ToolCall> toolCalls = msg.toolCalls();
        if (toolCalls != null) {
          messageParts.addAll(
              toolCalls.stream().map(this::toolCallToMessagePart).collect(Collectors.toList()));
        }
        inputMessages.append(InputMessage.create(Role.ASSISTANT, messageParts));
      } else if (msg.role() == ChatCompletionMessage.Role.TOOL) {
        inputMessages.append(
            InputMessage.create(
                Role.TOOL, contentToToolMessageParts(msg.toolCallId(), msg.rawContent())));
      }
    }
    return inputMessages;
  }

  @Nullable
  @Override
  public OutputMessages outputMessages(
      ChatCompletionRequest request, @Nullable ChatCompletion response) {
    if (!messageCaptureOptions.captureMessageContent()
        || response == null
        || response.choices() == null) {
      return null;
    }

    OutputMessages outputMessages = OutputMessages.create();
    for (Choice choice : response.choices()) {
      ChatCompletionMessage choiceMsg = choice.message();
      List<MessagePart> messageParts = new ArrayList<>();

      if (choiceMsg != null) {
        List<MessagePart> contentParts = contentToMessagePartsOrNull(choiceMsg.rawContent());
        if (contentParts != null) {
          messageParts.addAll(contentParts);
        }
        List<ToolCall> toolCalls = choiceMsg.toolCalls();
        if (toolCalls != null) {
          messageParts.addAll(
              toolCalls.stream().map(this::toolCallToMessagePart).collect(Collectors.toList()));
        }
      }

      outputMessages.append(
          OutputMessage.create(
              Role.ASSISTANT,
              messageParts,
              choice.finishReason() != null ? choice.finishReason().name().toLowerCase() : ""));
    }
    return outputMessages;
  }

  @Nullable
  @Override
  public SystemInstructions systemInstructions(
      ChatCompletionRequest request, @Nullable ChatCompletion response) {
    return null;
  }

  @Nullable
  @Override
  public ToolDefinitions toolDefinitions(
      ChatCompletionRequest request, @Nullable ChatCompletion response) {
    if (request.tools() == null) {
      return null;
    }

    ToolDefinitions toolDefinitions = ToolDefinitions.create();
    request.tools().stream()
        .filter(Objects::nonNull)
        .map(
            tool -> {
              if (tool.getFunction() != null) {
                String name = tool.getFunction().getName();
                String type = tool.getType().name().toLowerCase();
                if (messageCaptureOptions.captureMessageContent()
                    && tool.getFunction().getDescription() != null) {
                  return ToolDefinition.create(
                      type, name, tool.getFunction().getDescription(), null);
                } else {
                  return ToolDefinition.create(type, name, null, null);
                }
              }
              return null;
            })
        .filter(Objects::nonNull)
        .forEach(toolDefinitions::append);

    return toolDefinitions;
  }

  /**
   * Support content:
   *
   * <ul>
   *   <li>{@code String}
   *   <li>{@code List<String>}
   * </ul>
   */
  private List<MessagePart> contentToMessageParts(Object rawContent) {
    List<MessagePart> messageParts = contentToMessagePartsOrNull(rawContent);
    return messageParts == null ? Collections.singletonList(TextPart.create("")) : messageParts;
  }

  /**
   * Support content:
   *
   * <ul>
   *   <li>{@code String}
   *   <li>{@code List<String>}
   * </ul>
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  private List<MessagePart> contentToMessagePartsOrNull(Object rawContent) {
    if (rawContent instanceof String && !((String) rawContent).isEmpty()) {
      return Collections.singletonList(TextPart.create(truncateTextContent((String) rawContent)));
    } else if (rawContent instanceof List) {
      return joinContentParts((List) rawContent);
    } else {
      return null;
    }
  }

  private MessagePart toolCallToMessagePart(ToolCall call) {
    if (call != null && call.function() != null) {
      return ToolCallRequestPart.create(
          call.id(), call.function().name(), call.function().arguments());
    }
    return ToolCallRequestPart.create("unknown_function");
  }

  /**
   * Support content:
   *
   * <ul>
   *   <li>{@code String}
   *   <li>{@code List<String>}
   * </ul>
   */
  private List<MessagePart> contentToToolMessageParts(String toolCallId, Object rawContent) {
    if (rawContent instanceof String && !((String) rawContent).isEmpty()) {
      return Collections.singletonList(
          ToolCallResponsePart.create(toolCallId, truncateTextContent((String) rawContent)));
    }
    return Collections.singletonList(ToolCallResponsePart.create(toolCallId));
  }

  private List<MessagePart> joinContentParts(List<Object> contentParts) {
    return contentParts.stream()
        .filter(part -> part instanceof String)
        .map(part -> this.truncateTextContent((String) part))
        .map(TextPart::create)
        .collect(Collectors.toList());
  }

  private String truncateTextContent(String content) {
    if (!content.endsWith(TRUNCATE_FLAG)
        && content.length() > messageCaptureOptions.maxMessageContentLength()) {
      content =
          content.substring(0, messageCaptureOptions.maxMessageContentLength()) + TRUNCATE_FLAG;
    }
    return content;
  }
}
