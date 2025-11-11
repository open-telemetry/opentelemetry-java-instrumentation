/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.openai.v1_1;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.openai.v1_1.OpenAITelemetry;
import io.opentelemetry.javaagent.bootstrap.internal.AgentInstrumentationConfig;

public final class OpenAiSingletons {
  public static final OpenAITelemetry TELEMETRY =
      OpenAITelemetry.builder(GlobalOpenTelemetry.get())
          .setCaptureMessageContent(
              AgentInstrumentationConfig.get()
                  .getBoolean("otel.instrumentation.genai.capture-message-content", false))
          .setEmitExperimentalConventions(emitExperimentalConventions())
          .build();

  private static boolean emitExperimentalConventions() {
    String config = AgentInstrumentationConfig.get().getString("otel.semconv-stability.opt-in", "");
    return config.contains("gen_ai_latest_experimental");
  }

  private OpenAiSingletons() {}
}
