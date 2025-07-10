/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.openai.v1_1;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.openai.v1_1.OpenAITelemetry;
import io.opentelemetry.javaagent.bootstrap.internal.AgentInstrumentationConfig;

@SuppressWarnings("IdentifierName") // Want to match library's convention
public final class OpenAISingletons {
  public static final OpenAITelemetry TELEMETRY =
      OpenAITelemetry.builder(GlobalOpenTelemetry.get())
          .setCaptureMessageContent(
              AgentInstrumentationConfig.get()
                  .getBoolean("otel.instrumentation.genai.capture-message-content", false))
          .build();

  private OpenAISingletons() {}
}
