/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.openai;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.openai.models.FunctionDefinition;
import com.openai.models.chat.completions.ChatCompletionMessageToolCall;
import com.openai.models.chat.completions.ChatCompletionTool;

public interface TestHelper {

  String id(ChatCompletionMessageToolCall toolCall);

  String arguments(ChatCompletionMessageToolCall toolCall);

  ChatCompletionTool chatCompletionTool(FunctionDefinition functionDefinition);

  MessageToolCallBuilder messageToolCallBuilder();

  MessageToolCallBuilder.FunctionBuilder messageToolCallFunctionBuilder();

  interface MessageToolCallBuilder {
    @CanIgnoreReturnValue
    MessageToolCallBuilder id(String id);

    @CanIgnoreReturnValue
    MessageToolCallBuilder function(FunctionBuilder functionBuilder);

    ChatCompletionMessageToolCall build();

    interface FunctionBuilder {
      @CanIgnoreReturnValue
      FunctionBuilder name(String name);

      @CanIgnoreReturnValue
      FunctionBuilder arguments(String arguments);
    }
  }
}
