/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.springai.v1_0;

import static io.opentelemetry.semconv.incubating.EventIncubatingAttributes.EVENT_NAME;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_PROVIDER_NAME;

import io.opentelemetry.api.common.Value;
import io.opentelemetry.api.logs.LogRecordBuilder;
import io.opentelemetry.context.Context;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

public class SpringAiMessageEvents {
  public static void emitPromptEvents(Context context, SpringAiRequest request) {
    try {
      for (Message message : request.prompt().getInstructions()) {
        String eventName = eventName(message.getMessageType());
        if (eventName == null) {
          continue;
        }
        Map<String, Value<?>> body = new HashMap<>();
        if (SpringAiSingletons.captureMessageContent()) {
          String content = message.getText();
          if (content != null) {
            body.put("content", Value.of(content));
          }
        }
        newEvent(request, eventName).setContext(context).setBody(Value.of(body)).emit();
      }
    } catch (Throwable ignored) {
      // This helper can run outside of Byte Buddy advice for streaming calls.
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

    try {
      List<Generation> results = response.getResults();
      for (int index = 0; index < results.size(); index++) {
        Generation generation = results.get(index);
        Map<String, Value<?>> body = new HashMap<>();
        String finishReason = finishReason(generation);
        if (finishReason != null) {
          body.put("finish_reason", Value.of(finishReason));
        }
        body.put("index", Value.of(index));
        Map<String, Value<?>> message = new HashMap<>();
        if (SpringAiSingletons.captureMessageContent()) {
          String content =
              streamedContents != null && index < streamedContents.size()
                  ? streamedContents.get(index)
                  : generation.getOutput().getText();
          if (content != null) {
            message.put("content", Value.of(content));
          }
        }
        body.put("message", Value.of(message));
        newEvent(request, "gen_ai.choice").setContext(context).setBody(Value.of(body)).emit();
      }
    } catch (Throwable ignored) {
      // This helper can run outside of Byte Buddy advice for streaming calls.
    }
  }

  @SuppressWarnings("deprecation") // using deprecated semconv
  private static LogRecordBuilder newEvent(SpringAiRequest request, String eventName) {
    return SpringAiSingletons.eventLogger()
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

  private SpringAiMessageEvents() {}
}
