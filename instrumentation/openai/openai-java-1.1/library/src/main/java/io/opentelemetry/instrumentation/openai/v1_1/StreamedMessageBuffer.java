/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.openai.v1_1;

import static java.util.stream.Collectors.toList;

import com.openai.core.JsonField;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionChunk;
import com.openai.models.chat.completions.ChatCompletionMessage;
import com.openai.models.chat.completions.ChatCompletionMessageToolCall;
import io.opentelemetry.api.common.Value;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;

final class StreamedMessageBuffer {
  private final long index;
  private final boolean captureMessageContent;

  @Nullable String finishReason;

  @Nullable private StringBuilder message;
  @Nullable private Map<Long, ToolCallBuffer> toolCalls;

  StreamedMessageBuffer(long index, boolean captureMessageContent) {
    this.index = index;
    this.captureMessageContent = captureMessageContent;
  }

  ChatCompletion.Choice toChoice() {
    ChatCompletion.Choice.Builder choice =
        ChatCompletion.Choice.builder().index(index).logprobs(Optional.empty());
    if (finishReason != null) {
      choice.finishReason(ChatCompletion.Choice.FinishReason.of(finishReason));
    } else {
      // Can't happen in practice, mostly to satisfy null check
      choice.finishReason(JsonField.ofNullable(null));
    }
    ChatCompletionMessage.Builder msgBuilder =
        ChatCompletionMessage.builder()
            .content(message != null ? message.toString() : null)
            .refusal(Optional.empty());
    if (toolCalls != null) {
      List<ChatCompletionMessageToolCall> sdkToolCalls =
          toolCalls.values().stream()
              .map(StreamedMessageBuffer::toSdkToolCall)
              .filter(Objects::nonNull)
              .collect(toList());
      if (!sdkToolCalls.isEmpty()) {
        msgBuilder.toolCalls(sdkToolCalls);
      }
    }
    choice.message(msgBuilder.build());
    return choice.build();
  }

  Value<?> toEventBody() {
    Map<String, Value<?>> body = new HashMap<>();
    if (message != null) {
      body.put("content", Value.of(message.toString()));
    }
    if (toolCalls != null) {
      List<Value<?>> toolCallsJson =
          toolCalls.values().stream()
              .map(StreamedMessageBuffer::buildToolCallEventObject)
              .collect(toList());
      body.put("tool_calls", Value.of(toolCallsJson));
    }
    return Value.of(body);
  }

  void append(ChatCompletionChunk.Choice.Delta delta) {
    if (captureMessageContent) {
      if (delta.content().isPresent()) {
        if (message == null) {
          message = new StringBuilder();
        }
        message.append(delta.content().get());
      }
    }

    if (delta.toolCalls().isPresent()) {
      if (toolCalls == null) {
        toolCalls = new HashMap<>();
      }

      for (ChatCompletionChunk.Choice.Delta.ToolCall toolCall : delta.toolCalls().get()) {
        ToolCallBuffer buffer =
            toolCalls.computeIfAbsent(
                toolCall.index(), unused -> new ToolCallBuffer(toolCall.id().orElse("")));
        toolCall.type().ifPresent(type -> buffer.type = type.toString());
        toolCall
            .function()
            .ifPresent(
                function -> {
                  function.name().ifPresent(name -> buffer.function.name = name);
                  if (captureMessageContent) {
                    function
                        .arguments()
                        .ifPresent(
                            args -> {
                              if (buffer.function.arguments == null) {
                                buffer.function.arguments = new StringBuilder();
                              }
                              buffer.function.arguments.append(args);
                            });
                  }
                });
      }
    }
  }

  @Nullable
  private static ChatCompletionMessageToolCall toSdkToolCall(ToolCallBuffer tc) {
    String name = tc.function.name != null ? tc.function.name : "";
    String arguments = tc.function.arguments != null ? tc.function.arguments.toString() : "";
    if (V1ToolCallBuilder.isAvailable()) {
      return V1ToolCallBuilder.build(tc.id, name, arguments);
    }
    if (V3ToolCallBuilder.isAvailable()) {
      return V3ToolCallBuilder.build(tc.id, name, arguments);
    }
    return null;
  }

  // v1.x: ChatCompletionMessageToolCall.builder().id().function(Function.builder()...).build()
  private static class V1ToolCallBuilder {
    @Nullable private static final MethodHandle toolCallBuilderHandle;
    @Nullable private static final MethodHandle builderIdHandle;
    @Nullable private static final MethodHandle builderFunctionHandle;
    @Nullable private static final MethodHandle builderBuildHandle;
    @Nullable private static final MethodHandle funcBuilderHandle;
    @Nullable private static final MethodHandle funcBuilderNameHandle;
    @Nullable private static final MethodHandle funcBuilderArgumentsHandle;
    @Nullable private static final MethodHandle funcBuilderBuildHandle;

    static {
      MethodHandle tcBuilder = null;
      MethodHandle builderId = null;
      MethodHandle builderFunction = null;
      MethodHandle builderBuild = null;
      MethodHandle funcBuilder = null;
      MethodHandle funcName = null;
      MethodHandle funcArguments = null;
      MethodHandle funcBuild = null;
      try {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        Class<?> builderClass =
            Class.forName(
                "com.openai.models.chat.completions.ChatCompletionMessageToolCall$Builder");
        Class<?> functionClass =
            Class.forName(
                "com.openai.models.chat.completions.ChatCompletionMessageToolCall$Function");
        Class<?> funcBuilderClass =
            Class.forName(
                "com.openai.models.chat.completions.ChatCompletionMessageToolCall$Function$Builder");

        tcBuilder =
            lookup.findStatic(
                ChatCompletionMessageToolCall.class,
                "builder",
                MethodType.methodType(builderClass));
        builderId =
            lookup.findVirtual(
                builderClass, "id", MethodType.methodType(builderClass, String.class));
        builderFunction =
            lookup.findVirtual(
                builderClass, "function", MethodType.methodType(builderClass, functionClass));
        builderBuild =
            lookup.findVirtual(
                builderClass, "build", MethodType.methodType(ChatCompletionMessageToolCall.class));
        funcBuilder =
            lookup.findStatic(functionClass, "builder", MethodType.methodType(funcBuilderClass));
        funcName =
            lookup.findVirtual(
                funcBuilderClass, "name", MethodType.methodType(funcBuilderClass, String.class));
        funcArguments =
            lookup.findVirtual(
                funcBuilderClass,
                "arguments",
                MethodType.methodType(funcBuilderClass, String.class));
        funcBuild =
            lookup.findVirtual(funcBuilderClass, "build", MethodType.methodType(functionClass));
      } catch (Exception e) {
        tcBuilder = null;
        builderId = null;
        builderFunction = null;
        builderBuild = null;
        funcBuilder = null;
        funcName = null;
        funcArguments = null;
        funcBuild = null;
      }
      toolCallBuilderHandle = tcBuilder;
      builderIdHandle = builderId;
      builderFunctionHandle = builderFunction;
      builderBuildHandle = builderBuild;
      funcBuilderHandle = funcBuilder;
      funcBuilderNameHandle = funcName;
      funcBuilderArgumentsHandle = funcArguments;
      funcBuilderBuildHandle = funcBuild;
    }

    static boolean isAvailable() {
      return toolCallBuilderHandle != null;
    }

    @Nullable
    static ChatCompletionMessageToolCall build(String id, String name, String arguments) {
      if (toolCallBuilderHandle == null
          || builderIdHandle == null
          || builderFunctionHandle == null
          || builderBuildHandle == null
          || funcBuilderHandle == null
          || funcBuilderNameHandle == null
          || funcBuilderArgumentsHandle == null
          || funcBuilderBuildHandle == null) {
        return null;
      }
      try {
        Object fb = funcBuilderHandle.invoke();
        fb = funcBuilderNameHandle.invoke(fb, name);
        fb = funcBuilderArgumentsHandle.invoke(fb, arguments);
        Object function = funcBuilderBuildHandle.invoke(fb);

        Object b = toolCallBuilderHandle.invoke();
        b = builderIdHandle.invoke(b, id);
        b = builderFunctionHandle.invoke(b, function);
        return (ChatCompletionMessageToolCall) builderBuildHandle.invoke(b);
      } catch (Throwable ignore) {
        return null;
      }
    }
  }

  // v3+:
  // ChatCompletionMessageFunctionToolCall.builder().id().function(Function.builder()...).build()
  //      then ChatCompletionMessageToolCall.ofFunction(...)
  private static class V3ToolCallBuilder {
    @Nullable private static final MethodHandle ftcBuilderHandle;
    @Nullable private static final MethodHandle ftcBuilderIdHandle;
    @Nullable private static final MethodHandle ftcBuilderFunctionHandle;
    @Nullable private static final MethodHandle ftcBuilderBuildHandle;
    @Nullable private static final MethodHandle funcBuilderHandle;
    @Nullable private static final MethodHandle funcBuilderNameHandle;
    @Nullable private static final MethodHandle funcBuilderArgumentsHandle;
    @Nullable private static final MethodHandle funcBuilderBuildHandle;
    @Nullable private static final MethodHandle ofFunctionHandle;

    static {
      MethodHandle ftcBuilder = null;
      MethodHandle ftcBId = null;
      MethodHandle ftcBFunction = null;
      MethodHandle ftcBBuild = null;
      MethodHandle funcBuilder = null;
      MethodHandle funcName = null;
      MethodHandle funcArguments = null;
      MethodHandle funcBuild = null;
      MethodHandle ofFunction = null;
      try {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        Class<?> ftcClass =
            Class.forName(
                "com.openai.models.chat.completions.ChatCompletionMessageFunctionToolCall");
        Class<?> ftcBuilderClass =
            Class.forName(
                "com.openai.models.chat.completions.ChatCompletionMessageFunctionToolCall$Builder");
        Class<?> functionClass =
            Class.forName(
                "com.openai.models.chat.completions.ChatCompletionMessageFunctionToolCall$Function");
        Class<?> funcBuilderClass =
            Class.forName(
                "com.openai.models.chat.completions.ChatCompletionMessageFunctionToolCall$Function$Builder");

        ftcBuilder = lookup.findStatic(ftcClass, "builder", MethodType.methodType(ftcBuilderClass));
        ftcBId =
            lookup.findVirtual(
                ftcBuilderClass, "id", MethodType.methodType(ftcBuilderClass, String.class));
        ftcBFunction =
            lookup.findVirtual(
                ftcBuilderClass, "function", MethodType.methodType(ftcBuilderClass, functionClass));
        ftcBBuild = lookup.findVirtual(ftcBuilderClass, "build", MethodType.methodType(ftcClass));
        funcBuilder =
            lookup.findStatic(functionClass, "builder", MethodType.methodType(funcBuilderClass));
        funcName =
            lookup.findVirtual(
                funcBuilderClass, "name", MethodType.methodType(funcBuilderClass, String.class));
        funcArguments =
            lookup.findVirtual(
                funcBuilderClass,
                "arguments",
                MethodType.methodType(funcBuilderClass, String.class));
        funcBuild =
            lookup.findVirtual(funcBuilderClass, "build", MethodType.methodType(functionClass));
        ofFunction =
            lookup.findStatic(
                ChatCompletionMessageToolCall.class,
                "ofFunction",
                MethodType.methodType(ChatCompletionMessageToolCall.class, ftcClass));
      } catch (Exception e) {
        ftcBuilder = null;
        ftcBId = null;
        ftcBFunction = null;
        ftcBBuild = null;
        funcBuilder = null;
        funcName = null;
        funcArguments = null;
        funcBuild = null;
        ofFunction = null;
      }
      ftcBuilderHandle = ftcBuilder;
      ftcBuilderIdHandle = ftcBId;
      ftcBuilderFunctionHandle = ftcBFunction;
      ftcBuilderBuildHandle = ftcBBuild;
      funcBuilderHandle = funcBuilder;
      funcBuilderNameHandle = funcName;
      funcBuilderArgumentsHandle = funcArguments;
      funcBuilderBuildHandle = funcBuild;
      ofFunctionHandle = ofFunction;
    }

    static boolean isAvailable() {
      return ftcBuilderHandle != null;
    }

    @Nullable
    static ChatCompletionMessageToolCall build(String id, String name, String arguments) {
      if (ftcBuilderHandle == null
          || ftcBuilderIdHandle == null
          || ftcBuilderFunctionHandle == null
          || ftcBuilderBuildHandle == null
          || funcBuilderHandle == null
          || funcBuilderNameHandle == null
          || funcBuilderArgumentsHandle == null
          || funcBuilderBuildHandle == null
          || ofFunctionHandle == null) {
        return null;
      }
      try {
        Object fb = funcBuilderHandle.invoke();
        fb = funcBuilderNameHandle.invoke(fb, name);
        fb = funcBuilderArgumentsHandle.invoke(fb, arguments);
        Object function = funcBuilderBuildHandle.invoke(fb);

        Object b = ftcBuilderHandle.invoke();
        b = ftcBuilderIdHandle.invoke(b, id);
        b = ftcBuilderFunctionHandle.invoke(b, function);
        Object ftc = ftcBuilderBuildHandle.invoke(b);
        return (ChatCompletionMessageToolCall) ofFunctionHandle.invoke(ftc);
      } catch (Throwable ignore) {
        return null;
      }
    }
  }

  private static Value<?> buildToolCallEventObject(ToolCallBuffer call) {
    Map<String, Value<?>> result = new HashMap<>();
    result.put("id", Value.of(call.id));
    if (call.type != null) {
      result.put("type", Value.of(call.type));
    }

    Map<String, Value<?>> function = new HashMap<>();
    if (call.function.name != null) {
      function.put("name", Value.of(call.function.name));
    }
    if (call.function.arguments != null) {
      function.put("arguments", Value.of(call.function.arguments.toString()));
    }
    result.put("function", Value.of(function));

    return Value.of(result);
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
