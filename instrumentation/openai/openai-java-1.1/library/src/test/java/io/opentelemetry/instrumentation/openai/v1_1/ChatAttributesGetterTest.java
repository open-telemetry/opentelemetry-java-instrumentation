/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.openai.v1_1;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;

import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.completions.CompletionUsage;
import org.junit.jupiter.api.Test;

class ChatAttributesGetterTest {

  private static final ChatCompletionRequest REQUEST =
      ChatCompletionRequest.create(
          ChatCompletionCreateParams.builder().messages(emptyList()).model("test-model").build());

  private final ChatAttributesGetter getter = new ChatAttributesGetter();

  @Test
  void extractsDetailedTokenUsage() {
    CompletionUsage usage =
        CompletionUsage.builder()
            .promptTokens(20)
            .completionTokens(10)
            .totalTokens(30)
            .promptTokensDetails(
                CompletionUsage.PromptTokensDetails.builder().cachedTokens(12).build())
            .completionTokensDetails(
                CompletionUsage.CompletionTokensDetails.builder().reasoningTokens(4).build())
            .build();
    ChatCompletion response =
        ChatCompletion.builder()
            .id("test-id")
            .choices(emptyList())
            .created(0)
            .model("test-model")
            .usage(usage)
            .build();

    assertThat(getter.getUsageCacheReadInputTokens(REQUEST, response)).isEqualTo(12L);
    assertThat(getter.getUsageReasoningOutputTokens(REQUEST, response)).isEqualTo(4L);
  }

  @Test
  void omitsDetailedTokenUsageWhenDetailsAreUnavailable() {
    CompletionUsage usage =
        CompletionUsage.builder().promptTokens(20).completionTokens(10).totalTokens(30).build();
    ChatCompletion response =
        ChatCompletion.builder()
            .id("test-id")
            .choices(emptyList())
            .created(0)
            .model("test-model")
            .usage(usage)
            .build();

    assertThat(getter.getUsageCacheReadInputTokens(REQUEST, response)).isNull();
    assertThat(getter.getUsageReasoningOutputTokens(REQUEST, response)).isNull();
  }

  @Test
  void omitsDetailedTokenUsageWhenResponseIsUnavailable() {
    assertThat(getter.getUsageCacheReadInputTokens(REQUEST, null)).isNull();
    assertThat(getter.getUsageReasoningOutputTokens(REQUEST, null)).isNull();
  }
}
