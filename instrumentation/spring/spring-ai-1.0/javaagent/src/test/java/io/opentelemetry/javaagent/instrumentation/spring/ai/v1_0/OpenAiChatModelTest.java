/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.ai.v1_0;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

class OpenAiChatModelTest extends AbstractOpenAiChatModelTest {

  @Override
  protected ChatModel openAiChatModel(String baseUrl, String model) {
    OpenAiApi openAiApi =
        OpenAiApi.builder()
            .baseUrl(baseUrl)
            .apiKey("unused")
            .completionsPath("/chat/completions")
            .restClientBuilder(
                RestClient.builder().requestFactory(new SimpleClientHttpRequestFactory()))
            .build();
    return OpenAiChatModel.builder()
        .openAiApi(openAiApi)
        .defaultOptions(OpenAiChatOptions.builder().model(model).build())
        .build();
  }
}
