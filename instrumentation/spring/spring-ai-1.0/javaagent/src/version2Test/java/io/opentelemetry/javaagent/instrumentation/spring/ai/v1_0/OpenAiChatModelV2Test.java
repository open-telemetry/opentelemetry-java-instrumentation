/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.ai.v1_0;

import com.openai.client.OpenAIClientImpl;
import com.openai.core.ClientOptions;
import org.junit.jupiter.api.AfterEach;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.http.okhttp.SpringAiOpenAiHttpClient;

class OpenAiChatModelV2Test extends AbstractOpenAiChatModelTest {

  private SpringAiOpenAiHttpClient httpClient;

  @AfterEach
  void closeHttpClient() {
    if (httpClient != null) {
      // Shut down all OkHttp threads to prevent context propagation leaks between tests.
      httpClient.getOkHttpClient().dispatcher().executorService().shutdownNow();
      httpClient.getOkHttpClient().connectionPool().evictAll();
      httpClient.close();
      httpClient = null;
    }
  }

  @Override
  protected ChatModel openAiChatModel(String baseUrl, String model) {
    httpClient = SpringAiOpenAiHttpClient.builder().build();
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
