/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.springai.v1_0;

import static io.opentelemetry.api.common.AttributeKey.stringKey;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

/** Adds opt-in message content attributes for backends that display trace tags. */
public class SpringAiMessageAttributes {
  private static final AttributeKey<String> GEN_AI_INPUT_MESSAGES =
      stringKey("gen_ai.input.messages");
  private static final AttributeKey<String> GEN_AI_OUTPUT_MESSAGES =
      stringKey("gen_ai.output.messages");

  public static void setInputMessages(Context context, SpringAiRequest request) {
    if (!SpringAiSingletons.captureMessageContentAsSpanAttributes()) {
      return;
    }
    try {
      Span.fromContext(context)
          .setAttribute(
              GEN_AI_INPUT_MESSAGES,
              serializeMessages(
                  request.prompt().getInstructions(),
                  SpringAiSingletons.messageContentSpanAttributeMaxLength()));
    } catch (Throwable ignored) {
      // This helper can run outside of Byte Buddy advice for streaming calls.
    }
  }

  public static void setOutputMessages(
      Context context, @Nullable ChatResponse response, @Nullable List<String> streamedContents) {
    if (!SpringAiSingletons.captureMessageContentAsSpanAttributes() || response == null) {
      return;
    }
    try {
      Span.fromContext(context)
          .setAttribute(
              GEN_AI_OUTPUT_MESSAGES,
              serializeResponses(
                  response,
                  streamedContents,
                  SpringAiSingletons.messageContentSpanAttributeMaxLength()));
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
      result.append("{\"role\":");
      appendJsonString(result, message.getMessageType().name().toLowerCase(Locale.ROOT));
      result.append(",\"parts\":[{\"type\":\"text\",\"content\":");
      appendJsonString(result, truncate(message.getText(), maxContentLength));
      result.append("}]}");
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
      appendAssistantMessage(result, content, finishReason(generation), maxContentLength);
    }
    return result.append(']').toString();
  }

  private static void appendAssistantMessage(
      StringBuilder result,
      @Nullable String content,
      @Nullable String finishReason,
      int maxContentLength) {
    result.append("{\"role\":\"assistant\",\"parts\":[{\"type\":\"text\",\"content\":");
    appendJsonString(result, truncate(content, maxContentLength));
    result.append("}],\"finish_reason\":");
    appendJsonString(result, finishReason == null ? "unknown" : finishReason);
    result.append('}');
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
