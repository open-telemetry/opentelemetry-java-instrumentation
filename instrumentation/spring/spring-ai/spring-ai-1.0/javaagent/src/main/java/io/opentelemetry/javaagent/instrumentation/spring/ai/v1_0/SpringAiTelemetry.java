/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.ai.v1_0;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.genai.MessageCaptureOptions;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.reactor.v3_1.ContextPropagationOperator;
import io.opentelemetry.javaagent.instrumentation.spring.ai.v1_0.tool.ToolCallRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.ChatClientRequest;
import reactor.core.publisher.Flux;

public final class SpringAiTelemetry {

  public static SpringAiTelemetryBuilder builder(OpenTelemetry openTelemetry) {
    return new SpringAiTelemetryBuilder(openTelemetry);
  }
  private final Instrumenter<ChatClientRequest, ChatClientResponse> chatClientInstrumenter;
  private final Instrumenter<ToolCallRequest, String> toolCallInstrumenter;
  private final MessageCaptureOptions messageCaptureOptions;

  SpringAiTelemetry(
      Instrumenter<ChatClientRequest, ChatClientResponse> chatClientInstrumenter,
      Instrumenter<ToolCallRequest, String> toolCallInstrumenter,
      MessageCaptureOptions messageCaptureOptions) {
    this.chatClientInstrumenter = chatClientInstrumenter;
    this.toolCallInstrumenter = toolCallInstrumenter;
    this.messageCaptureOptions = messageCaptureOptions;
  }

  public Instrumenter<ChatClientRequest, ChatClientResponse> chatClientInstrumenter() {
    return chatClientInstrumenter;
  }

  public Instrumenter<ToolCallRequest, String> toolCallInstrumenter() {
    return toolCallInstrumenter;
  }

  public MessageCaptureOptions messageCaptureOptions() {
    return messageCaptureOptions;
  }
}
