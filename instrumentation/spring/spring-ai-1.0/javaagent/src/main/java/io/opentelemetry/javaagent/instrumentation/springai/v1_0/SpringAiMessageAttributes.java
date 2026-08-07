/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.springai.v1_0;

import static io.opentelemetry.api.common.AttributeKey.booleanKey;
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
public final class SpringAiMessageAttributes {
  private static final AttributeKey<String> GEN_AI_INPUT_MESSAGES =
      stringKey("gen_ai.input.messages");
  private static final AttributeKey<String> GEN_AI_OUTPUT_MESSAGES =
      stringKey("gen_ai.output.messages");
  private static final AttributeKey<Boolean> GEN_AI_INPUT_MESSAGES_TRUNCATED =
      booleanKey("gen_ai.input.messages.truncated");
  private static final AttributeKey<Boolean> GEN_AI_OUTPUT_MESSAGES_TRUNCATED =
      booleanKey("gen_ai.output.messages.truncated");

  public static void setInputMessages(Context context, SpringAiRequest request) {
    if (!SpringAiSingletons.captureMessageContentAsSpanAttributes()) {
      return;
    }
    Span span = Span.fromContext(context);
    SerializedMessages messages =
        serializeMessages(
            request.prompt().getInstructions(),
            SpringAiSingletons.messageContentSpanAttributeMaxLength());
    span.setAttribute(GEN_AI_INPUT_MESSAGES, messages.json());
    if (messages.truncated()) {
      span.setAttribute(GEN_AI_INPUT_MESSAGES_TRUNCATED, true);
    }
  }

  public static void setOutputMessages(
      Context context, @Nullable ChatResponse response, @Nullable String streamedContent) {
    if (!SpringAiSingletons.captureMessageContentAsSpanAttributes() || response == null) {
      return;
    }
    Span span = Span.fromContext(context);
    SerializedMessages messages =
        serializeResponses(
            response, streamedContent, SpringAiSingletons.messageContentSpanAttributeMaxLength());
    span.setAttribute(GEN_AI_OUTPUT_MESSAGES, messages.json());
    if (messages.truncated()) {
      span.setAttribute(GEN_AI_OUTPUT_MESSAGES_TRUNCATED, true);
    }
  }

  static SerializedMessages serializeMessages(List<Message> messages, int maxContentLength) {
    StringBuilder result = new StringBuilder("[");
    boolean truncated = false;
    for (int index = 0; index < messages.size(); index++) {
      if (index > 0) {
        result.append(',');
      }
      Message message = messages.get(index);
      result.append("{\"role\":");
      appendJsonString(result, message.getMessageType().name().toLowerCase(Locale.ROOT));
      result.append(",\"content\":");
      TruncatedContent content = truncate(message.getText(), maxContentLength);
      appendJsonString(result, content.value());
      result.append('}');
      truncated |= content.truncated();
    }
    return new SerializedMessages(result.append(']').toString(), truncated);
  }

  static SerializedMessages serializeResponses(
      ChatResponse response, @Nullable String streamedContent, int maxContentLength) {
    StringBuilder result = new StringBuilder("[");
    boolean truncated = false;
    if (streamedContent != null) {
      truncated = appendAssistantMessage(result, streamedContent, maxContentLength);
    } else {
      List<Generation> generations = response.getResults();
      for (int index = 0; index < generations.size(); index++) {
        if (index > 0) {
          result.append(',');
        }
        truncated |=
            appendAssistantMessage(
                result, generations.get(index).getOutput().getText(), maxContentLength);
      }
    }
    return new SerializedMessages(result.append(']').toString(), truncated);
  }

  private static boolean appendAssistantMessage(
      StringBuilder result, @Nullable String content, int maxContentLength) {
    result.append("{\"role\":\"assistant\",\"content\":");
    TruncatedContent truncatedContent = truncate(content, maxContentLength);
    appendJsonString(result, truncatedContent.value());
    result.append('}');
    return truncatedContent.truncated();
  }

  private static TruncatedContent truncate(@Nullable String content, int maxContentLength) {
    if (content == null || content.length() <= maxContentLength) {
      return new TruncatedContent(content, false);
    }
    int end = maxContentLength;
    if (end > 0
        && end < content.length()
        && Character.isHighSurrogate(content.charAt(end - 1))
        && Character.isLowSurrogate(content.charAt(end))) {
      end--;
    }
    return new TruncatedContent(content.substring(0, end), true);
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

  static final class SerializedMessages {
    private final String json;
    private final boolean truncated;

    private SerializedMessages(String json, boolean truncated) {
      this.json = json;
      this.truncated = truncated;
    }

    String json() {
      return json;
    }

    boolean truncated() {
      return truncated;
    }
  }

  private static final class TruncatedContent {
    @Nullable private final String value;
    private final boolean truncated;

    private TruncatedContent(@Nullable String value, boolean truncated) {
      this.value = value;
      this.truncated = truncated;
    }

    @Nullable
    private String value() {
      return value;
    }

    private boolean truncated() {
      return truncated;
    }
  }
}
