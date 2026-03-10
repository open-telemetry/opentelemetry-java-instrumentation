/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.openai.v3_0;

import com.openai.models.FunctionDefinition;
import com.openai.models.chat.completions.ChatCompletionFunctionTool;
import com.openai.models.chat.completions.ChatCompletionMessageFunctionToolCall;
import com.openai.models.chat.completions.ChatCompletionMessageToolCall;
import com.openai.models.chat.completions.ChatCompletionTool;
import io.opentelemetry.instrumentation.openai.TestHelper;
import io.opentelemetry.instrumentation.openai.TestHelper.MessageToolCallBuilder.FunctionBuilder;

public class OpenAi3TestHelper implements TestHelper {
  @Override
  public String id(ChatCompletionMessageToolCall toolCall) {
    return toolCall.function().get().id();
  }

  @Override
  public String arguments(ChatCompletionMessageToolCall toolCall) {
    return toolCall.function().get().function().arguments();
  }

  @Override
  public ChatCompletionTool chatCompletionTool(FunctionDefinition functionDefinition) {
    return ChatCompletionTool.ofFunction(
        ChatCompletionFunctionTool.builder().function(functionDefinition).build());
  }

  @Override
  public MessageToolCallBuilder messageToolCallBuilder() {
    return new MessageToolCallBuilder() {
      final ChatCompletionMessageFunctionToolCall.Builder builder =
          ChatCompletionMessageFunctionToolCall.builder();

      @Override
      public MessageToolCallBuilder id(String id) {
        builder.id(id);
        return this;
      }

      @Override
      public MessageToolCallBuilder function(FunctionBuilder functionBuilder) {
        builder.function(((FunctionBuilderImpl) functionBuilder).builder.build());
        return null;
      }

      @Override
      public ChatCompletionMessageToolCall build() {
        return ChatCompletionMessageToolCall.ofFunction(builder.build());
      }
    };
  }

  @Override
  public FunctionBuilder messageToolCallFunctionBuilder() {
    return new FunctionBuilderImpl();
  }

  private static class FunctionBuilderImpl implements FunctionBuilder {
    final ChatCompletionMessageFunctionToolCall.Function.Builder builder =
        ChatCompletionMessageFunctionToolCall.Function.builder();

    @Override
    public FunctionBuilder name(String name) {
      builder.name(name);
      return this;
    }

    @Override
    public FunctionBuilder arguments(String arguments) {
      builder.arguments(arguments);
      return this;
    }
  }
}
