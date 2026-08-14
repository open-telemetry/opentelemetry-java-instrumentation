/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.springai.v1_0;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.javaagent.instrumentation.springai.v1_0.SpringAiSingletons.captureMessageContentAsSpanAttributes;
import static io.opentelemetry.javaagent.instrumentation.springai.v1_0.SpringAiSingletons.messageContentSpanAttributeMaxLength;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.content.Media;
import org.springframework.ai.content.MediaContent;
import org.springframework.util.MimeType;

/** Adds opt-in message content attributes for backends that display trace tags. */
public class SpringAiMessageAttributes {
  private static final AttributeKey<String> GEN_AI_INPUT_MESSAGES =
      stringKey("gen_ai.input.messages");
  private static final AttributeKey<String> GEN_AI_OUTPUT_MESSAGES =
      stringKey("gen_ai.output.messages");

  public static void setInputMessages(Context context, SpringAiRequest request) {
    if (!captureMessageContentAsSpanAttributes()) {
      return;
    }
    try {
      Span.fromContext(context)
          .setAttribute(
              GEN_AI_INPUT_MESSAGES,
              serializeMessages(
                  request.prompt().getInstructions(), messageContentSpanAttributeMaxLength()));
    } catch (Throwable ignored) {
      // This helper can run outside of Byte Buddy advice for streaming calls.
    }
  }

  public static void setOutputMessages(
      Context context, @Nullable ChatResponse response, @Nullable List<String> streamedContents) {
    if (!captureMessageContentAsSpanAttributes() || response == null) {
      return;
    }
    try {
      Span.fromContext(context)
          .setAttribute(
              GEN_AI_OUTPUT_MESSAGES,
              serializeResponses(
                  response, streamedContents, messageContentSpanAttributeMaxLength()));
    } catch (Throwable ignored) {
      // This helper can run outside of Byte Buddy advice for streaming calls.
    }
  }

  static String serializeMessages(List<Message> messages, int maxContentLength) {
    StringBuilder result = new StringBuilder("[");
    for (int index = 0; index < messages.size(); index++) {
      if (index > 0) {
        result.append(',');
      }
      Message message = messages.get(index);
      appendMessage(result, message, null, null, maxContentLength);
    }
    return result.append(']').toString();
  }

  static String serializeResponses(
      ChatResponse response, @Nullable List<String> streamedContents, int maxContentLength) {
    StringBuilder result = new StringBuilder("[");
    List<Generation> generations = response.getResults();
    for (int index = 0; index < generations.size(); index++) {
      if (index > 0) {
        result.append(',');
      }
      Generation generation = generations.get(index);
      String content =
          streamedContents != null && index < streamedContents.size()
              ? streamedContents.get(index)
              : generation.getOutput().getText();
      String finishReason = finishReason(generation);
      appendMessage(
          result,
          generation.getOutput(),
          content,
          finishReason == null ? "unknown" : finishReason,
          maxContentLength);
    }
    return result.append(']').toString();
  }

  private static void appendMessage(
      StringBuilder result,
      Message message,
      @Nullable String contentOverride,
      @Nullable String finishReason,
      int maxContentLength) {
    result.append("{\"role\":");
    appendJsonString(result, message.getMessageType().name().toLowerCase(Locale.ROOT));
    result.append(",\"parts\":[");
    appendParts(result, message, contentOverride, maxContentLength);
    result.append(']');
    if (finishReason != null) {
      result.append(",\"finish_reason\":");
      appendJsonString(result, finishReason);
    }
    result.append('}');
  }

  private static void appendParts(
      StringBuilder result,
      Message message,
      @Nullable String contentOverride,
      int maxContentLength) {
    boolean hasStructuredParts = hasStructuredParts(message);
    String content = contentOverride != null ? contentOverride : message.getText();
    boolean hasParts = false;
    if (content != null && (!content.isEmpty() || !hasStructuredParts)) {
      hasParts = appendTextPart(result, false, content, maxContentLength);
    }
    hasParts = appendAssistantToolCallParts(result, hasParts, message, maxContentLength);
    hasParts = appendToolResponseParts(result, hasParts, message, maxContentLength);
    appendMediaParts(result, hasParts, message, maxContentLength);
  }

  private static boolean hasStructuredParts(Message message) {
    if (message instanceof AssistantMessage assistantMessage) {
      List<AssistantMessage.ToolCall> toolCalls = assistantMessage.getToolCalls();
      if (toolCalls != null && !toolCalls.isEmpty()) {
        return true;
      }
    }
    if (message instanceof ToolResponseMessage toolResponseMessage) {
      List<ToolResponseMessage.ToolResponse> responses = toolResponseMessage.getResponses();
      if (responses != null && !responses.isEmpty()) {
        return true;
      }
    }
    if (message instanceof MediaContent mediaContent) {
      List<Media> media = mediaContent.getMedia();
      return media != null && !media.isEmpty();
    }
    return false;
  }

  private static boolean appendTextPart(
      StringBuilder result, boolean hasParts, String content, int maxContentLength) {
    appendPartSeparator(result, hasParts);
    result.append("{\"type\":\"text\",\"content\":");
    appendJsonString(result, truncate(content, maxContentLength));
    result.append('}');
    return true;
  }

  private static boolean appendAssistantToolCallParts(
      StringBuilder result, boolean hasParts, Message message, int maxContentLength) {
    if (!(message instanceof AssistantMessage assistantMessage)) {
      return hasParts;
    }
    List<AssistantMessage.ToolCall> toolCalls = assistantMessage.getToolCalls();
    if (toolCalls == null || toolCalls.isEmpty()) {
      return hasParts;
    }
    for (AssistantMessage.ToolCall toolCall : toolCalls) {
      appendPartSeparator(result, hasParts);
      result.append("{\"type\":\"tool_call\"");
      appendOptionalJsonStringField(result, "id", toolCall.id());
      result.append(",\"name\":");
      appendJsonString(result, toolCall.name());
      appendOptionalJsonStringField(
          result, "arguments", truncate(toolCall.arguments(), maxContentLength));
      result.append('}');
      hasParts = true;
    }
    return hasParts;
  }

  private static boolean appendToolResponseParts(
      StringBuilder result, boolean hasParts, Message message, int maxContentLength) {
    if (!(message instanceof ToolResponseMessage toolResponseMessage)) {
      return hasParts;
    }
    List<ToolResponseMessage.ToolResponse> responses = toolResponseMessage.getResponses();
    if (responses == null || responses.isEmpty()) {
      return hasParts;
    }
    for (ToolResponseMessage.ToolResponse response : responses) {
      appendPartSeparator(result, hasParts);
      result.append("{\"type\":\"tool_call_response\"");
      appendOptionalJsonStringField(result, "id", response.id());
      appendOptionalJsonStringField(result, "name", response.name());
      result.append(",\"response\":");
      appendJsonString(result, truncate(response.responseData(), maxContentLength));
      result.append('}');
      hasParts = true;
    }
    return hasParts;
  }

  private static boolean appendMediaParts(
      StringBuilder result, boolean hasParts, Message message, int maxContentLength) {
    if (!(message instanceof MediaContent mediaContent)) {
      return hasParts;
    }
    List<Media> media = mediaContent.getMedia();
    if (media == null || media.isEmpty()) {
      return hasParts;
    }
    for (Media item : media) {
      hasParts = appendMediaPart(result, hasParts, item, maxContentLength);
    }
    return hasParts;
  }

  private static boolean appendMediaPart(
      StringBuilder result, boolean hasParts, Media media, int maxContentLength) {
    MimeType mimeType = media.getMimeType();
    Object data = media.getData();
    appendPartSeparator(result, hasParts);
    String uri = uriString(data);
    if (uri != null) {
      result.append("{\"type\":\"uri\"");
      appendMediaMetadata(result, mimeType);
      result.append(",\"uri\":");
      appendJsonString(result, truncate(uri, maxContentLength));
      result.append('}');
      return true;
    }
    if (media.getId() != null && !media.getId().isEmpty()) {
      result.append("{\"type\":\"file\"");
      appendMediaMetadata(result, mimeType);
      result.append(",\"file_id\":");
      appendJsonString(result, media.getId());
      result.append('}');
      return true;
    }
    result.append("{\"type\":\"media\"");
    appendMediaMetadata(result, mimeType);
    appendOptionalJsonStringField(result, "name", media.getName());
    result.append('}');
    return true;
  }

  @Nullable
  private static String uriString(Object data) {
    if (data instanceof URI uri) {
      return uri.toString();
    }
    if (data instanceof String string) {
      try {
        if (URI.create(string).isAbsolute()) {
          return string;
        }
      } catch (IllegalArgumentException ignored) {
        // Not a URI string.
      }
    }
    return null;
  }

  private static void appendMediaMetadata(StringBuilder result, @Nullable MimeType mimeType) {
    appendOptionalJsonStringField(
        result, "mime_type", mimeType == null ? null : mimeType.toString());
    result.append(",\"modality\":");
    appendJsonString(result, modality(mimeType));
  }

  private static String modality(@Nullable MimeType mimeType) {
    if (mimeType == null) {
      return "unknown";
    }
    String type = mimeType.getType().toLowerCase(Locale.ROOT);
    if (type.equals("image") || type.equals("audio") || type.equals("video")) {
      return type;
    }
    return "file";
  }

  private static void appendPartSeparator(StringBuilder result, boolean hasParts) {
    if (hasParts) {
      result.append(',');
    }
  }

  private static void appendOptionalJsonStringField(
      StringBuilder result, String name, @Nullable String value) {
    if (value == null) {
      return;
    }
    result.append(",\"");
    result.append(name);
    result.append("\":");
    appendJsonString(result, value);
  }

  @Nullable
  private static String finishReason(Generation generation) {
    return generation.getMetadata() == null ? null : generation.getMetadata().getFinishReason();
  }

  @Nullable
  private static String truncate(@Nullable String content, int maxContentLength) {
    if (content == null || content.length() <= maxContentLength) {
      return content;
    }
    int end = maxContentLength;
    if (end > 0
        && end < content.length()
        && Character.isHighSurrogate(content.charAt(end - 1))
        && Character.isLowSurrogate(content.charAt(end))) {
      end--;
    }
    return content.substring(0, end);
  }

  private static void appendJsonString(StringBuilder result, @Nullable String value) {
    result.append('"');
    String text = value == null ? "" : value;
    for (int index = 0; index < text.length(); index++) {
      char character = text.charAt(index);
      if (character == '"' || character == '\\') {
        result.append('\\').append(character);
      } else if (character == '\b') {
        result.append("\\b");
      } else if (character == '\f') {
        result.append("\\f");
      } else if (character == '\n') {
        result.append("\\n");
      } else if (character == '\r') {
        result.append("\\r");
      } else if (character == '\t') {
        result.append("\\t");
      } else if (character < 0x20) {
        result.append("\\u");
        String hex = Integer.toHexString(character);
        for (int zeroes = hex.length(); zeroes < 4; zeroes++) {
          result.append('0');
        }
        result.append(hex);
      } else {
        result.append(character);
      }
    }
    result.append('"');
  }

  private SpringAiMessageAttributes() {}
}
