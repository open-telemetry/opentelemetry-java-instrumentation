/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.springai.v1_0;

import static io.opentelemetry.javaagent.instrumentation.springai.v1_0.SpringAiRequest.providerName;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SpringAiRequestTest {

  @Test
  void mapsKnownProvidersToSemanticConventionValues() {
    assertThat(providerName("org.springframework.ai.azure.openai.AzureOpenAiChatModel"))
        .isEqualTo("azure.ai.openai");
    assertThat(providerName("org.springframework.ai.bedrock.converse.BedrockConverseChatModel"))
        .isEqualTo("aws.bedrock");
    assertThat(providerName("org.springframework.ai.vertexai.gemini.VertexAiGeminiChatModel"))
        .isEqualTo("gcp.vertex_ai");
    assertThat(providerName("org.springframework.ai.google.genai.GoogleGenAiChatModel"))
        .isEqualTo("gcp.gemini");
    assertThat(providerName("org.springframework.ai.anthropic.AnthropicChatModel"))
        .isEqualTo("anthropic");
    assertThat(providerName("org.springframework.ai.deepseek.DeepSeekChatModel"))
        .isEqualTo("deepseek");
    assertThat(providerName("org.springframework.ai.mistralai.MistralAiChatModel"))
        .isEqualTo("mistral_ai");
    assertThat(providerName("org.springframework.ai.moonshot.MoonshotChatModel"))
        .isEqualTo("moonshot_ai");
    assertThat(providerName("org.springframework.ai.openai.OpenAiChatModel")).isEqualTo("openai");
    assertThat(providerName("org.springframework.ai.watsonx.ai.WatsonxAiChatModel"))
        .isEqualTo("ibm.watsonx.ai");
  }

  @Test
  void derivesStableFallbackForCustomAndProxiedModels() {
    assertThat(providerName("example.CustomChatModel$$SpringCGLIB$$0")).isEqualTo("custom");
    assertThat(providerName("example.ChatModel")).isEqualTo("spring-ai");
  }
}
