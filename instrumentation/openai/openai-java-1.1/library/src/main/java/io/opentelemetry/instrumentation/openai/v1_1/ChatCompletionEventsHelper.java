/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.openai.v1_1;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.instrumentation.openai.v1_1.GenAiAttributes.GEN_AI_SYSTEM;

import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionAssistantMessageParam;
import com.openai.models.chat.completions.ChatCompletionContentPartText;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionDeveloperMessageParam;
import com.openai.models.chat.completions.ChatCompletionMessage;
import com.openai.models.chat.completions.ChatCompletionMessageParam;
import com.openai.models.chat.completions.ChatCompletionMessageToolCall;
import com.openai.models.chat.completions.ChatCompletionSystemMessageParam;
import com.openai.models.chat.completions.ChatCompletionToolMessageParam;
import com.openai.models.chat.completions.ChatCompletionUserMessageParam;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Value;
import io.opentelemetry.api.logs.LogRecordBuilder;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.context.Context;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

final class ChatCompletionEventsHelper {

  private static final AttributeKey<String> EVENT_NAME = stringKey("event.name");

  public static void emitPromptLogEvents(
      Logger eventLogger, ChatCompletionCreateParams request, boolean captureMessageContent) {
    for (ChatCompletionMessageParam msg : request.messages()) {
      String eventType;
      Map<String, Value<?>> body = new HashMap<>();
      if (msg.isSystem()) {
        eventType = "gen_ai.system.message";
        if (captureMessageContent) {
          body.put("content", Value.of(contentToString(msg.asSystem().content())));
        }
      } else if (msg.isDeveloper()) {
        eventType = "gen_ai.system.message";
        body.put("role", Value.of("developer"));
        if (captureMessageContent) {
          body.put("content", Value.of(contentToString(msg.asDeveloper().content())));
        }
      } else if (msg.isUser()) {
        eventType = "gen_ai.user.message";
        if (captureMessageContent) {
          body.put("content", Value.of(contentToString(msg.asUser().content())));
        }
      } else if (msg.isAssistant()) {
        ChatCompletionAssistantMessageParam assistantMsg = msg.asAssistant();
        eventType = "gen_ai.assistant.message";
        if (captureMessageContent) {
          assistantMsg
              .content()
              .ifPresent(content -> body.put("content", Value.of(contentToString(content))));
        }
        assistantMsg
            .toolCalls()
            .ifPresent(
                toolCalls -> {
                  List<Value<?>> toolCallsJson =
                      toolCalls.stream()
                          .map(call -> buildToolCallEventObject(call, captureMessageContent))
                          .collect(Collectors.toList());
                  body.put("tool_calls", Value.of(toolCallsJson));
                });
      } else if (msg.isTool()) {
        ChatCompletionToolMessageParam toolMsg = msg.asTool();
        eventType = "gen_ai.tool.message";
        if (captureMessageContent) {
          body.put("content", Value.of(contentToString(toolMsg.content())));
        }
        body.put("id", Value.of(toolMsg.toolCallId()));
      } else {
        continue;
      }
      newEvent(eventLogger, eventType).setBody(Value.of(body)).emit();
    }
  }

  private static String contentToString(ChatCompletionToolMessageParam.Content content) {
    if (content.isText()) {
      return content.asText();
    } else if (content.isArrayOfContentParts()) {
      return joinContentParts(content.asArrayOfContentParts());
    } else {
      return "";
    }
  }

  private static String contentToString(ChatCompletionAssistantMessageParam.Content content) {
    if (content.isText()) {
      return content.asText();
    } else if (content.isArrayOfContentParts()) {
      return content.asArrayOfContentParts().stream()
          .map(
              part -> {
                if (part.isText()) {
                  return part.asText().text();
                }
                if (part.isRefusal()) {
                  return part.asRefusal().refusal();
                }
                return null;
              })
          .filter(Objects::nonNull)
          .collect(Collectors.joining());
    } else {
      return "";
    }
  }

  private static String contentToString(ChatCompletionSystemMessageParam.Content content) {
    if (content.isText()) {
      return content.asText();
    } else if (content.isArrayOfContentParts()) {
      return joinContentParts(content.asArrayOfContentParts());
    } else {
      return "";
    }
  }

  private static String contentToString(ChatCompletionDeveloperMessageParam.Content content) {
    if (content.isText()) {
      return content.asText();
    } else if (content.isArrayOfContentParts()) {
      return joinContentParts(content.asArrayOfContentParts());
    } else {
      return "";
    }
  }

  private static String contentToString(ChatCompletionUserMessageParam.Content content) {
    if (content.isText()) {
      return content.asText();
    } else if (content.isArrayOfContentParts()) {
      return content.asArrayOfContentParts().stream()
          .map(part -> part.isText() ? part.asText().text() : null)
          .filter(Objects::nonNull)
          .collect(Collectors.joining());
    } else {
      return "";
    }
  }

  private static String joinContentParts(List<ChatCompletionContentPartText> contentParts) {
    return contentParts.stream()
        .map(ChatCompletionContentPartText::text)
        .collect(Collectors.joining());
  }

  public static void emitCompletionLogEvents(
      Logger eventLogger, ChatCompletion completion, boolean captureMessageContent) {
    for (ChatCompletion.Choice choice : completion.choices()) {
      ChatCompletionMessage choiceMsg = choice.message();
      Map<String, Value<?>> message = new HashMap<>();
      if (captureMessageContent) {
        choiceMsg.content().ifPresent(content -> message.put("content", Value.of(content)));
      }
      choiceMsg
          .toolCalls()
          .ifPresent(
              toolCalls -> {
                message.put(
                    "tool_calls",
                    Value.of(
                        toolCalls.stream()
                            .map(call -> buildToolCallEventObject(call, captureMessageContent))
                            .collect(Collectors.toList())));
              });
      emitCompletionLogEvent(
          eventLogger, choice.index(), choice.finishReason().toString(), Value.of(message), null);
    }
  }

  public static void emitCompletionLogEvent(
      Logger eventLogger,
      long index,
      String finishReason,
      Value<?> eventMessageObject,
      @Nullable Context contextOverride) {
    Map<String, Value<?>> body = new HashMap<>();
    body.put("finish_reason", Value.of(finishReason));
    body.put("index", Value.of(index));
    body.put("message", eventMessageObject);
    LogRecordBuilder builder = newEvent(eventLogger, "gen_ai.choice").setBody(Value.of(body));
    if (contextOverride != null) {
      builder.setContext(contextOverride);
    }
    builder.emit();
  }

  private static LogRecordBuilder newEvent(Logger eventLogger, String name) {
    return eventLogger
        .logRecordBuilder()
        .setAttribute(EVENT_NAME, name)
        .setAttribute(GEN_AI_SYSTEM, "openai");
  }

  private static Value<?> buildToolCallEventObject(
      ChatCompletionMessageToolCall call, boolean captureMessageContent) {
    Map<String, Value<?>> result = new HashMap<>();
    result.put("id", Value.of(call.id()));
    result.put("type", Value.of("function")); // "function" is the only currently supported type
    result.put("function", buildFunctionEventObject(call.function(), captureMessageContent));
    return Value.of(result);
  }

  private static Value<?> buildFunctionEventObject(
      ChatCompletionMessageToolCall.Function function, boolean captureMessageContent) {
    Map<String, Value<?>> result = new HashMap<>();
    result.put("name", Value.of(function.name()));
    if (captureMessageContent) {
      result.put("arguments", Value.of(function.arguments()));
    }
    return Value.of(result);
  }

  private ChatCompletionEventsHelper() {}
}
