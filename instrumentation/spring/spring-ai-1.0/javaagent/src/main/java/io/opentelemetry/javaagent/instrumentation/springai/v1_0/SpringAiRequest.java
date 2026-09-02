/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.springai.v1_0;

import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

public class SpringAiRequest {
  private final Prompt prompt;
  @Nullable private final ChatOptions defaultOptions;
  private final String provider;
  private final boolean streaming;

  public static SpringAiRequest create(Prompt prompt, Object chatModel, boolean streaming) {
    ChatOptions defaultOptions =
        chatModel instanceof ChatModel model ? model.getDefaultOptions() : null;
    return new SpringAiRequest(
        prompt, defaultOptions, providerName(chatModel.getClass().getName()), streaming);
  }

  private SpringAiRequest(
      Prompt prompt, @Nullable ChatOptions defaultOptions, String provider, boolean streaming) {
    this.prompt = prompt;
    this.defaultOptions = defaultOptions;
    this.provider = provider;
    this.streaming = streaming;
  }

  public Prompt prompt() {
    return prompt;
  }

  public String provider() {
    return provider;
  }

  public boolean streaming() {
    return streaming;
  }

  @Nullable
  public String model() {
    ChatOptions options = prompt.getOptions();
    String model = options == null ? null : options.getModel();
    return model != null || defaultOptions == null ? model : defaultOptions.getModel();
  }

  @Nullable
  public Double frequencyPenalty() {
    ChatOptions options = prompt.getOptions();
    Double value = options == null ? null : options.getFrequencyPenalty();
    return value != null || defaultOptions == null ? value : defaultOptions.getFrequencyPenalty();
  }

  @Nullable
  public Integer maxTokens() {
    ChatOptions options = prompt.getOptions();
    Integer value = options == null ? null : options.getMaxTokens();
    return value != null || defaultOptions == null ? value : defaultOptions.getMaxTokens();
  }

  @Nullable
  public Double presencePenalty() {
    ChatOptions options = prompt.getOptions();
    Double value = options == null ? null : options.getPresencePenalty();
    return value != null || defaultOptions == null ? value : defaultOptions.getPresencePenalty();
  }

  @Nullable
  public List<String> stopSequences() {
    ChatOptions options = prompt.getOptions();
    List<String> value = options == null ? null : options.getStopSequences();
    return value != null || defaultOptions == null ? value : defaultOptions.getStopSequences();
  }

  @Nullable
  public Double temperature() {
    ChatOptions options = prompt.getOptions();
    Double value = options == null ? null : options.getTemperature();
    return value != null || defaultOptions == null ? value : defaultOptions.getTemperature();
  }

  @Nullable
  public Integer topK() {
    ChatOptions options = prompt.getOptions();
    Integer value = options == null ? null : options.getTopK();
    return value != null || defaultOptions == null ? value : defaultOptions.getTopK();
  }

  @Nullable
  public Double topP() {
    ChatOptions options = prompt.getOptions();
    Double value = options == null ? null : options.getTopP();
    return value != null || defaultOptions == null ? value : defaultOptions.getTopP();
  }

  static String providerName(String className) {
    String lowerClassName = className.toLowerCase(Locale.ROOT);
    if (lowerClassName.contains(".azure.openai.")
        || lowerClassName.contains("azureopenaichatmodel")) {
      return "azure.ai.openai";
    }
    if (lowerClassName.contains(".bedrock.")) {
      return "aws.bedrock";
    }
    if (lowerClassName.contains(".vertexai.")) {
      return "gcp.vertex_ai";
    }
    if (lowerClassName.contains(".google.genai.")) {
      return "gcp.gemini";
    }
    if (lowerClassName.contains(".anthropic.")) {
      return "anthropic";
    }
    if (lowerClassName.contains(".deepseek.")) {
      return "deepseek";
    }
    if (lowerClassName.contains(".mistralai.")) {
      return "mistral_ai";
    }
    if (lowerClassName.contains(".moonshot.")) {
      return "moonshot_ai";
    }
    if (lowerClassName.contains(".openai.")) {
      return "openai";
    }
    if (lowerClassName.contains(".watsonx.")) {
      return "ibm.watsonx.ai";
    }

    int packageMarker = className.lastIndexOf('.');
    if (packageMarker >= 0) {
      className = className.substring(packageMarker + 1);
    }
    int proxyMarker = className.indexOf('$');
    if (proxyMarker >= 0) {
      className = className.substring(0, proxyMarker);
    }
    if (className.endsWith("ChatModel")) {
      className = className.substring(0, className.length() - "ChatModel".length());
    }
    return className.isEmpty() ? "spring-ai" : className.toLowerCase(Locale.ROOT);
  }
}
