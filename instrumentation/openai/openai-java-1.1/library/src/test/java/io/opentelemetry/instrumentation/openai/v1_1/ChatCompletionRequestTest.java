/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.openai.v1_1;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;

import com.openai.models.chat.completions.ChatCompletionCreateParams;
import org.junit.jupiter.api.Test;

class ChatCompletionRequestTest {

  private static final ChatCompletionCreateParams REQUEST =
      ChatCompletionCreateParams.builder().messages(emptyList()).model("test-model").build();

  @Test
  void createsStreamingRequest() {
    ChatCompletionRequest request = ChatCompletionRequest.createStreaming(REQUEST);

    assertThat(request.getRequest()).isSameAs(REQUEST);
    assertThat(request.isStreaming()).isTrue();
  }

  @Test
  void createsNonStreamingRequest() {
    ChatCompletionRequest request = ChatCompletionRequest.create(REQUEST);

    assertThat(request.getRequest()).isSameAs(REQUEST);
    assertThat(request.isStreaming()).isFalse();
  }
}
