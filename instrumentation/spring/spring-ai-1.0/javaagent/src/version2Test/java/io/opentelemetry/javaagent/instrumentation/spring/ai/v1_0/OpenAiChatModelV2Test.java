/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.ai.v1_0;

import com.openai.client.OpenAIClientImpl;
import com.openai.core.ClientOptions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.http.okhttp.SpringAiOpenAiHttpClient;

class OpenAiChatModelV2Test extends AbstractOpenAiChatModelTest {

  private static SpringAiOpenAiHttpClient httpClient;

  @BeforeAll
  static void setUp() {
    httpClient = SpringAiOpenAiHttpClient.builder().build();
  }

  @AfterAll
  static void tearDown() {
    httpClient.close();
  }

  @Override
  protected ChatModel openAiChatModel(String baseUrl, String model) {
    OpenAIClientImpl openAiClient =
        new OpenAIClientImpl(
            ClientOptions.builder()
                .baseUrl(baseUrl)
                .apiKey("unused")
                .httpClient(httpClient)
                .build());
    return OpenAiChatModel.builder()
        .openAiClient(openAiClient)
        .openAiClientAsync(openAiClient.async())
        .options(OpenAiChatOptions.builder().model(model).build())
        .build();
  }
}
