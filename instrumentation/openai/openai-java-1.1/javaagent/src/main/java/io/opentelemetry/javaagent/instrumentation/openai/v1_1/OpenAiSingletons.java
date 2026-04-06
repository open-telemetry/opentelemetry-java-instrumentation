/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.openai.v1_1;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.openai.v1_1.OpenAITelemetry;

public class OpenAiSingletons {
  private static final OpenAITelemetry telemetry =
      OpenAITelemetry.builder(GlobalOpenTelemetry.get())
          .setCaptureMessageContent(
              DeclarativeConfigUtil.getInstrumentationConfig(GlobalOpenTelemetry.get(), "common")
                  .get("gen_ai")
                  .getBoolean("capture_message_content", false))
          .build();

  public static OpenAITelemetry telemetry() {
    return telemetry;
  }

  private OpenAiSingletons() {}
}
