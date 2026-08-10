/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.springframework.ai.openai;

import io.opentelemetry.javaagent.instrumentation.springai.v1_0.app.TestChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;

public class OpenAiChatModel extends TestChatModel {
  public OpenAiChatModel(ChatOptions defaultOptions) {
    super(defaultOptions);
  }
}
