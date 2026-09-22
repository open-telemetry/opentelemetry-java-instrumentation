/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.ai.v1_0;

import java.util.List;
import javax.annotation.Nullable;
import org.springframework.ai.chat.model.ChatResponse;

final class SpringAiResponse {
  private final ChatResponse response;
  @Nullable private final List<String> streamedContents;

  SpringAiResponse(ChatResponse response, @Nullable List<String> streamedContents) {
    this.response = response;
    this.streamedContents = streamedContents;
  }

  ChatResponse response() {
    return response;
  }

  @Nullable
  List<String> streamedContents() {
    return streamedContents;
  }
}
