/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.ai.v1_0;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SpringAiRequestTest {

  @ParameterizedTest
  @CsvSource({
    "org.springframework.ai.azure.openai.AzureOpenAiChatModel, azure.ai.openai",
    "com.example.AzureOpenAiChatModel, azure.ai.openai",
    "org.springframework.ai.bedrock.converse.BedrockConverseChatModel, aws.bedrock",
    "org.springframework.ai.vertexai.gemini.VertexAiGeminiChatModel, gcp.vertex_ai",
    "org.springframework.ai.google.genai.GoogleGenAiChatModel, gcp.gemini",
    "org.springframework.ai.anthropic.AnthropicChatModel, anthropic",
    "org.springframework.ai.deepseek.DeepSeekChatModel, deepseek",
    "org.springframework.ai.mistralai.MistralAiChatModel, mistral_ai",
    "org.springframework.ai.moonshot.MoonshotChatModel, moonshot_ai",
    "org.springframework.ai.openai.OpenAiChatModel, openai",
    "org.springframework.ai.watsonx.WatsonxAiChatModel, ibm.watsonx.ai",
    "com.example.CustomChatModel, custom",
    "com.example.CustomChatModel$Proxy, custom",
    "ChatModel, spring-ai"
  })
  void mapsProviderName(String className, String expectedProviderName) {
    assertThat(SpringAiRequest.providerName(className)).isEqualTo(expectedProviderName);
  }
}
