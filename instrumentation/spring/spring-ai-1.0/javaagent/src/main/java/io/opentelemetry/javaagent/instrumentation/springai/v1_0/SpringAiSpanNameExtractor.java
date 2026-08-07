/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.springai.v1_0;

import org.springframework.ai.chat.prompt.ChatOptions;

final class SpringAiSpanNameExtractor {
  static String name(SpringAiRequest request) {
    ChatOptions options = request.prompt().getOptions();
    String model = options == null ? null : options.getModel();
    return model == null ? "chat" : "chat " + model;
  }

  private SpringAiSpanNameExtractor() {}
}
