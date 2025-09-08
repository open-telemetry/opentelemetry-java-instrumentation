/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.openai.v1_1;

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
import io.opentelemetry.instrumentation.api.incubator.semconv.genai.messages.InputMessage;
import io.opentelemetry.instrumentation.api.incubator.semconv.genai.messages.InputMessages;
import io.opentelemetry.instrumentation.api.incubator.semconv.genai.messages.MessagePart;
import io.opentelemetry.instrumentation.api.incubator.semconv.genai.messages.OutputMessage;
import io.opentelemetry.instrumentation.api.incubator.semconv.genai.messages.OutputMessages;
import io.opentelemetry.instrumentation.api.incubator.semconv.genai.messages.Role;
import io.opentelemetry.instrumentation.api.incubator.semconv.genai.messages.TextPart;
import io.opentelemetry.instrumentation.api.incubator.semconv.genai.messages.ToolCallRequestPart;
import io.opentelemetry.instrumentation.api.incubator.semconv.genai.messages.ToolCallResponsePart;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

// as a field of Attributes Extractor
// replace the 'ChatCompletionEventsHelper'
public final class ChatCompletionMessagesConverter {

  private final boolean captureMessageContent;

  public ChatCompletionMessagesConverter(boolean captureMessageContent) {
    this.captureMessageContent = captureMessageContent;
  }

  public InputMessages createInputMessages(
      ChatCompletionCreateParams request) {
    if (!captureMessageContent) {
      return null;
    }

    InputMessages inputMessages = InputMessages.create();
    for (ChatCompletionMessageParam msg : request.messages()) {

      if (msg.isSystem()) {
        inputMessages.append(InputMessage.create(Role.SYSTEM, contentToMessageParts(msg.asSystem().content())));
      } else if (msg.isDeveloper()) {
        inputMessages.append(InputMessage.create(Role.DEVELOPER, contentToMessageParts(msg.asDeveloper().content())));
      } else if (msg.isUser()) {
        inputMessages.append(InputMessage.create(Role.USER, contentToMessageParts(msg.asUser().content())));
      } else if (msg.isAssistant()) {
        ChatCompletionAssistantMessageParam assistantMsg = msg.asAssistant();
        List<MessagePart> messageParts = new ArrayList<>();
        assistantMsg
            .content()
            .ifPresent(content -> messageParts.addAll(contentToMessageParts(content)));

        assistantMsg
            .toolCalls().ifPresent(toolCalls -> {
              messageParts.addAll(toolCalls.stream()
                  .map(ChatCompletionMessagesConverter::toolCallToMessagePart)
                  .filter(Objects::nonNull)
                  .collect(Collectors.toList()));
            });
        inputMessages.append(InputMessage.create(Role.ASSISTANT, messageParts));
      } else if (msg.isTool()) {
        ChatCompletionToolMessageParam toolMsg = msg.asTool();
        inputMessages.append(InputMessage.create(Role.TOOL, contentToMessageParts(toolMsg.toolCallId(), toolMsg.content())));
      } else {
        continue;
      }
    }
    return inputMessages;
  }

  public OutputMessages createOutputMessages(
      ChatCompletion completion) {
    if (!captureMessageContent) {
      return null;
    }

    OutputMessages outputMessages = OutputMessages.create();
    for (ChatCompletion.Choice choice : completion.choices()) {
      ChatCompletionMessage choiceMsg = choice.message();
      List<MessagePart> messageParts = new ArrayList<>();

      choiceMsg
          .content()
          .ifPresent(
              content -> messageParts.add(TextPart.create(content)));
      choiceMsg
          .toolCalls()
          .ifPresent(
              toolCalls -> {
                messageParts.addAll(
                    toolCalls.stream()
                    .map(ChatCompletionMessagesConverter::toolCallToMessagePart)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList()));
              });

      outputMessages.append(
          OutputMessage.create(
              Role.ASSISTANT,
              messageParts,
              choice.finishReason().asString()));
    }
    return outputMessages;
  }

  private static List<MessagePart> contentToMessageParts(String toolCallId, ChatCompletionToolMessageParam.Content content) {
    if (content.isText()) {
      return Collections.singletonList(ToolCallResponsePart.create(toolCallId, content.asText()));
    } else if (content.isArrayOfContentParts()) {
      return content.asArrayOfContentParts().stream()
          .map(ChatCompletionContentPartText::text)
          .map(response -> ToolCallResponsePart.create(toolCallId, response))
          .collect(Collectors.toList());
    } else {
      return Collections.singletonList(ToolCallResponsePart.create(""));
    }
  }

  private static List<MessagePart> contentToMessageParts(ChatCompletionAssistantMessageParam.Content content) {
    if (content.isText()) {
      return Collections.singletonList(TextPart.create(content.asText()));
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
          .map(TextPart::create)
          .collect(Collectors.toList());
    } else {
      return Collections.singletonList(TextPart.create(""));
    }
  }

  private static List<MessagePart> contentToMessageParts(ChatCompletionSystemMessageParam.Content content) {
    if (content.isText()) {
      return Collections.singletonList(TextPart.create(content.asText()));
    } else if (content.isArrayOfContentParts()) {
      return joinContentParts(content.asArrayOfContentParts());
    } else {
      return Collections.singletonList(TextPart.create(""));
    }
  }

  private static List<MessagePart> contentToMessageParts(ChatCompletionDeveloperMessageParam.Content content) {
    if (content.isText()) {
      return Collections.singletonList(TextPart.create(content.asText()));
    } else if (content.isArrayOfContentParts()) {
      return joinContentParts(content.asArrayOfContentParts());
    } else {
      return Collections.singletonList(TextPart.create(""));
    }
  }

  private static List<MessagePart> contentToMessageParts(ChatCompletionUserMessageParam.Content content) {
    if (content.isText()) {
      return Collections.singletonList(TextPart.create(content.asText()));
    } else if (content.isArrayOfContentParts()) {
      return content.asArrayOfContentParts().stream()
          .map(part -> part.isText() ? part.asText().text() : null)
          .filter(Objects::nonNull)
          .map(TextPart::create)
          .collect(Collectors.toList());
    } else {
      return Collections.singletonList(TextPart.create(""));
    }
  }

  private static List<MessagePart> joinContentParts(List<ChatCompletionContentPartText> contentParts) {
    return contentParts.stream()
        .map(ChatCompletionContentPartText::text)
        .map(TextPart::create)
        .collect(Collectors.toList());
  }

  private static MessagePart toolCallToMessagePart(ChatCompletionMessageToolCall call) {
    FunctionAccess functionAccess = getFunctionAccess(call);
    if (functionAccess != null) {
      return ToolCallRequestPart.create(functionAccess.id(), functionAccess.name(), functionAccess.arguments());
    }
    return null;
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
    @SuppressWarnings("unchecked")
    static FunctionAccess create(ChatCompletionMessageToolCall toolCall) {
      if (functionToolCallHandle == null || functionHandle == null) {
        return null;
      }

      try {
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
}
