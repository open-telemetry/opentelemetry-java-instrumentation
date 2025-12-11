/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.openai.v1_1;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.openai.v1_1.OpenAITelemetry;

public final class OpenAiSingletons {
  public static final OpenAITelemetry TELEMETRY =
      OpenAITelemetry.builder(GlobalOpenTelemetry.get())
          .setCaptureMessageContent(
              DeclarativeConfigUtil.getBoolean(
                      GlobalOpenTelemetry.get(), "genai", "capture_message_content")
                  .orElse(false))
          .build();

  private OpenAiSingletons() {}
}
