/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.openai.v1_1;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.instrumentation.openai.v1_1.GenAiAttributes.GEN_AI_PROVIDER_NAME;

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
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

final class ChatCompletionEventsHelper {

  private static final AttributeKey<String> EVENT_NAME = stringKey("event.name");

  public static void emitPromptLogEvents(
      Context context,
      Logger eventLogger,
      ChatCompletionCreateParams request,
      boolean captureMessageContent) {
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
      newEvent(eventLogger, eventType).setContext(context).setBody(Value.of(body)).emit();
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
      Context context,
      Logger eventLogger,
      ChatCompletion completion,
      boolean captureMessageContent) {
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
          context,
          eventLogger,
          choice.index(),
          choice.finishReason().toString(),
          Value.of(message));
    }
  }

  public static void emitCompletionLogEvent(
      Context context,
      Logger eventLogger,
      long index,
      String finishReason,
      Value<?> eventMessageObject) {
    Map<String, Value<?>> body = new HashMap<>();
    body.put("finish_reason", Value.of(finishReason));
    body.put("index", Value.of(index));
    body.put("message", eventMessageObject);
    newEvent(eventLogger, "gen_ai.choice").setContext(context).setBody(Value.of(body)).emit();
  }

  private static LogRecordBuilder newEvent(Logger eventLogger, String name) {
    return eventLogger
        .logRecordBuilder()
        .setAttribute(EVENT_NAME, name)
        .setAttribute(GEN_AI_PROVIDER_NAME, "openai");
  }

  private static Value<?> buildToolCallEventObject(
      ChatCompletionMessageToolCall call, boolean captureMessageContent) {
    Map<String, Value<?>> result = new HashMap<>();
    FunctionAccess functionAccess = getFunctionAccess(call);
    if (functionAccess != null) {
      result.put("id", Value.of(functionAccess.id()));
      result.put("type", Value.of("function")); // "function" is the only currently supported type
      result.put("function", buildFunctionEventObject(functionAccess, captureMessageContent));
    }
    return Value.of(result);
  }

  private static Value<?> buildFunctionEventObject(
      FunctionAccess functionAccess, boolean captureMessageContent) {
    Map<String, Value<?>> result = new HashMap<>();
    result.put("name", Value.of(functionAccess.name()));
    if (captureMessageContent) {
      result.put("arguments", Value.of(functionAccess.arguments()));
    }
    return Value.of(result);
  }

  @Nullable
  private static FunctionAccess getFunctionAccess(ChatCompletionMessageToolCall call) {
    if (V1FunctionAccess.isAvailable()) {
      return V1FunctionAccess.create(call);
    }
    if (V3FunctionAccess.isAvailable()) {
      return V3FunctionAccess.create(call);
    }

    return null;
  }

  private interface FunctionAccess {
    String id();

    String name();

    String arguments();
  }

  private static String invokeStringHandle(@Nullable MethodHandle methodHandle, Object object) {
    if (methodHandle == null) {
      return "";
    }

    try {
      return (String) methodHandle.invoke(object);
    } catch (Throwable ignore) {
      return "";
    }
  }

  private static class V1FunctionAccess implements FunctionAccess {
    @Nullable private static final MethodHandle idHandle;
    @Nullable private static final MethodHandle functionHandle;
    @Nullable private static final MethodHandle nameHandle;
    @Nullable private static final MethodHandle argumentsHandle;

    static {
      MethodHandle id;
      MethodHandle function;
      MethodHandle name;
      MethodHandle arguments;

      try {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        id =
            lookup.findVirtual(
                ChatCompletionMessageToolCall.class, "id", MethodType.methodType(String.class));
        Class<?> functionClass =
            Class.forName(
                "com.openai.models.chat.completions.ChatCompletionMessageToolCall$Function");
        function =
            lookup.findVirtual(
                ChatCompletionMessageToolCall.class,
                "function",
                MethodType.methodType(functionClass));
        name = lookup.findVirtual(functionClass, "name", MethodType.methodType(String.class));
        arguments =
            lookup.findVirtual(functionClass, "arguments", MethodType.methodType(String.class));
      } catch (Exception exception) {
        id = null;
        function = null;
        name = null;
        arguments = null;
      }
      idHandle = id;
      functionHandle = function;
      nameHandle = name;
      argumentsHandle = arguments;
    }

    private final ChatCompletionMessageToolCall toolCall;
    private final Object function;

    V1FunctionAccess(ChatCompletionMessageToolCall toolCall, Object function) {
      this.toolCall = toolCall;
      this.function = function;
    }

    @Nullable
    static FunctionAccess create(ChatCompletionMessageToolCall toolCall) {
      if (functionHandle == null) {
        return null;
      }

      try {
        return new V1FunctionAccess(toolCall, functionHandle.invoke(toolCall));
      } catch (Throwable ignore) {
        return null;
      }
    }

    static boolean isAvailable() {
      return idHandle != null;
    }

    @Override
    public String id() {
      return invokeStringHandle(idHandle, toolCall);
    }

    @Override
    public String name() {
      return invokeStringHandle(nameHandle, function);
    }

    @Override
    public String arguments() {
      return invokeStringHandle(argumentsHandle, function);
    }
  }

  static class V3FunctionAccess implements FunctionAccess {
    @Nullable private static final MethodHandle functionToolCallHandle;
    @Nullable private static final MethodHandle idHandle;
    @Nullable private static final MethodHandle functionHandle;
    @Nullable private static final MethodHandle nameHandle;
    @Nullable private static final MethodHandle argumentsHandle;

    static {
      MethodHandle functionToolCall;
      MethodHandle id;
      MethodHandle function;
      MethodHandle name;
      MethodHandle arguments;

      try {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        functionToolCall =
            lookup.findVirtual(
                ChatCompletionMessageToolCall.class,
                "function",
                MethodType.methodType(Optional.class));
        Class<?> functionToolCallClass =
            Class.forName(
                "com.openai.models.chat.completions.ChatCompletionMessageFunctionToolCall");
        id = lookup.findVirtual(functionToolCallClass, "id", MethodType.methodType(String.class));
        Class<?> functionClass =
            Class.forName(
                "com.openai.models.chat.completions.ChatCompletionMessageFunctionToolCall$Function");
        function =
            lookup.findVirtual(
                functionToolCallClass, "function", MethodType.methodType(functionClass));
        name = lookup.findVirtual(functionClass, "name", MethodType.methodType(String.class));
        arguments =
            lookup.findVirtual(functionClass, "arguments", MethodType.methodType(String.class));
      } catch (Exception exception) {
        functionToolCall = null;
        id = null;
        function = null;
        name = null;
        arguments = null;
      }
      functionToolCallHandle = functionToolCall;
      idHandle = id;
      functionHandle = function;
      nameHandle = name;
      argumentsHandle = arguments;
    }

    private final Object functionToolCall;
    private final Object function;

    V3FunctionAccess(Object functionToolCall, Object function) {
      this.functionToolCall = functionToolCall;
      this.function = function;
    }

    @Nullable
    static FunctionAccess create(ChatCompletionMessageToolCall toolCall) {
      if (functionToolCallHandle == null || functionHandle == null) {
        return null;
      }

      try {
        @SuppressWarnings("unchecked") // casting MethodHandle.invoke result
        Optional<Object> optional = (Optional<Object>) functionToolCallHandle.invoke(toolCall);
        if (!optional.isPresent()) {
          return null;
        }
        Object functionToolCall = optional.get();
        return new V3FunctionAccess(functionToolCall, functionHandle.invoke(functionToolCall));
      } catch (Throwable ignore) {
        return null;
      }
    }

    static boolean isAvailable() {
      return idHandle != null;
    }

    @Override
    public String id() {
      return invokeStringHandle(idHandle, functionToolCall);
    }

    @Override
    public String name() {
      return invokeStringHandle(nameHandle, function);
    }

    @Override
    public String arguments() {
      return invokeStringHandle(argumentsHandle, function);
    }
  }

  private ChatCompletionEventsHelper() {}
}
