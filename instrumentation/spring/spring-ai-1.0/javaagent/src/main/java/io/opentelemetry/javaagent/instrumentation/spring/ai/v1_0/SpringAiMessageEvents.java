/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.ai.v1_0;

import static io.opentelemetry.javaagent.instrumentation.spring.ai.v1_0.SpringAiSingletons.captureMessageContent;
import static io.opentelemetry.javaagent.instrumentation.spring.ai.v1_0.SpringAiSingletons.eventLogger;
import static io.opentelemetry.semconv.incubating.EventIncubatingAttributes.EVENT_NAME;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_PROVIDER_NAME;

import io.opentelemetry.api.common.Value;
import io.opentelemetry.api.logs.LogRecordBuilder;
import io.opentelemetry.context.Context;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

public class SpringAiMessageEvents {
  public static void emitPromptEvents(Context context, SpringAiRequest request) {
    for (Message message : request.prompt().getInstructions()) {
      if (message instanceof ToolResponseMessage toolResponseMessage) {
        emitToolResponseEvents(context, request, toolResponseMessage);
        continue;
      }
      String eventName = eventName(message.getMessageType());
      if (eventName == null) {
        continue;
      }
      Map<String, Value<?>> body = new LinkedHashMap<>();
      if (captureMessageContent()) {
        String content = message.getText();
        if (content != null && (!content.isEmpty() || !hasToolContent(message))) {
          body.put("content", Value.of(content));
        }
      }
      addToolCalls(body, message);
      newEvent(request, eventName).setContext(context).setBody(Value.of(body)).emit();
    }
  }

  public static void emitResponseEvents(
      Context context,
      SpringAiRequest request,
      @Nullable ChatResponse response,
      @Nullable List<String> streamedContents) {
    if (response == null) {
      return;
    }

    List<Generation> results = response.getResults();
    for (int index = 0; index < results.size(); index++) {
      Generation generation = results.get(index);
      Map<String, Value<?>> body = new LinkedHashMap<>();
      String finishReason = finishReason(generation);
      if (finishReason != null) {
        body.put("finish_reason", Value.of(finishReason));
      }
      body.put("index", Value.of(choiceIndex(generation, index)));
      Map<String, Value<?>> message = new LinkedHashMap<>();
      if (captureMessageContent()) {
        String content =
            streamedContents != null && index < streamedContents.size()
                ? streamedContents.get(index)
                : generation.getOutput().getText();
        if (content != null) {
          message.put("content", Value.of(content));
        }
      }
      addToolCalls(message, generation.getOutput());
      body.put("message", Value.of(message));
      newEvent(request, "gen_ai.choice").setContext(context).setBody(Value.of(body)).emit();
    }
  }

  @SuppressWarnings("deprecation") // using deprecated semconv
  private static LogRecordBuilder newEvent(SpringAiRequest request, String eventName) {
    return eventLogger()
        .logRecordBuilder()
        .setAttribute(EVENT_NAME, eventName)
        .setAttribute(GEN_AI_PROVIDER_NAME, request.provider());
  }

  @Nullable
  private static String eventName(MessageType messageType) {
    if (messageType == MessageType.SYSTEM) {
      return "gen_ai.system.message";
    }
    if (messageType == MessageType.USER) {
      return "gen_ai.user.message";
    }
    if (messageType == MessageType.ASSISTANT) {
      return "gen_ai.assistant.message";
    }
    if (messageType == MessageType.TOOL) {
      return "gen_ai.tool.message";
    }
    return null;
  }

  @Nullable
  private static String finishReason(Generation generation) {
    ChatGenerationMetadata metadata = generation.getMetadata();
    return metadata == null ? null : metadata.getFinishReason();
  }

  private static boolean hasToolContent(Message message) {
    if (message instanceof AssistantMessage assistantMessage) {
      List<AssistantMessage.ToolCall> toolCalls = assistantMessage.getToolCalls();
      return toolCalls != null && !toolCalls.isEmpty();
    }
    return false;
  }

  private static void addToolCalls(Map<String, Value<?>> body, Message message) {
    if (!(message instanceof AssistantMessage assistantMessage)) {
      return;
    }
    List<AssistantMessage.ToolCall> toolCalls = assistantMessage.getToolCalls();
    if (toolCalls == null || toolCalls.isEmpty()) {
      return;
    }
    List<Value<?>> values = new ArrayList<>(toolCalls.size());
    for (AssistantMessage.ToolCall toolCall : toolCalls) {
      values.add(toolCallEventValue(toolCall));
    }
    body.put("tool_calls", Value.of(values));
  }

  private static Value<?> toolCallEventValue(AssistantMessage.ToolCall toolCall) {
    Map<String, Value<?>> result = new LinkedHashMap<>();
    Map<String, Value<?>> function = new LinkedHashMap<>();
    putString(function, "name", toolCall.name());
    if (captureMessageContent()) {
      putString(function, "arguments", toolCall.arguments());
    }
    result.put("function", Value.of(function));
    putString(result, "id", toolCall.id());
    putString(result, "type", toolCall.type());
    return Value.of(result);
  }

  private static void emitToolResponseEvents(
      Context context, SpringAiRequest request, ToolResponseMessage toolResponseMessage) {
    List<ToolResponseMessage.ToolResponse> responses = toolResponseMessage.getResponses();
    if (responses == null || responses.isEmpty()) {
      return;
    }
    for (ToolResponseMessage.ToolResponse response : responses) {
      String id = response.id();
      if (id == null) {
        continue;
      }
      Map<String, Value<?>> body = new LinkedHashMap<>();
      body.put("id", Value.of(id));
      putString(body, "name", response.name());
      if (captureMessageContent()) {
        putString(body, "content", response.responseData());
      }
      newEvent(request, "gen_ai.tool.message").setContext(context).setBody(Value.of(body)).emit();
    }
  }

  static int choiceIndex(Generation generation, int fallback) {
    Map<String, Object> metadata = generation.getOutput().getMetadata();
    if (metadata == null) {
      return fallback;
    }
    Object value = metadata.get("index");
    if (!(value instanceof Number number)) {
      return fallback;
    }
    long index = number.longValue();
    return index >= 0 && index <= Integer.MAX_VALUE ? (int) index : fallback;
  }

  private static void putString(Map<String, Value<?>> values, String key, @Nullable String value) {
    if (value != null) {
      values.put(key, Value.of(value));
    }
  }

  private SpringAiMessageEvents() {}
}
