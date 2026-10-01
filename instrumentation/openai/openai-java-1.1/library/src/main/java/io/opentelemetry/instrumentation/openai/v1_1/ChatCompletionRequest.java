/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.openai.v1_1;

import com.openai.models.chat.completions.ChatCompletionCreateParams;

final class ChatCompletionRequest {

  private final ChatCompletionCreateParams request;
  private final boolean streaming;

  static ChatCompletionRequest create(ChatCompletionCreateParams request) {
    return new ChatCompletionRequest(request, false);
  }

  static ChatCompletionRequest createStreaming(ChatCompletionCreateParams request) {
    return new ChatCompletionRequest(request, true);
  }

  private ChatCompletionRequest(ChatCompletionCreateParams request, boolean streaming) {
    this.request = request;
    this.streaming = streaming;
  }

  ChatCompletionCreateParams getRequest() {
    return request;
  }

  boolean isStreaming() {
    return streaming;
  }
}
