/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.ai.v1_0;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.javaagent.instrumentation.spring.ai.v1_0.app.TestChatModel;
import io.opentelemetry.javaagent.testing.common.AgentClassLoaderAccess;
import java.lang.reflect.Method;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SpringAiRequestTest {

  private static final Method providerNameMethod = providerNameMethod();

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
  void mapsProviderName(String className, String expectedProviderName) throws Exception {
    assertThat(providerNameMethod.invoke(null, className)).isEqualTo(expectedProviderName);
  }

  private static Method providerNameMethod() {
    try {
      String requestClassName =
          "io.opentelemetry.javaagent.instrumentation.spring.ai.v1_0.SpringAiRequest";
      ClassLoader modelClassLoader = TestChatModel.class.getClassLoader();
      Class<?> requestClass;
      try {
        requestClass = Class.forName(requestClassName, true, modelClassLoader);
      } catch (ClassNotFoundException ignored) {
        Class<?> registry =
            AgentClassLoaderAccess.loadClass(
                "io.opentelemetry.javaagent.tooling.instrumentation.indy.IndyModuleRegistry");
        Method getInstrumentationClassLoader =
            registry.getMethod("getInstrumentationClassLoader", String.class, ClassLoader.class);
        ClassLoader instrumentationClassLoader =
            (ClassLoader)
                getInstrumentationClassLoader.invoke(
                    null,
                    "io.opentelemetry.javaagent.instrumentation.spring.ai.v1_0."
                        + "SpringAiInstrumentationModule",
                    modelClassLoader);
        requestClass = Class.forName(requestClassName, true, instrumentationClassLoader);
      }
      Method method = requestClass.getDeclaredMethod("providerName", String.class);
      method.setAccessible(true);
      return method;
    } catch (ReflectiveOperationException e) {
      throw new LinkageError(e.getMessage(), e);
    }
  }
}
