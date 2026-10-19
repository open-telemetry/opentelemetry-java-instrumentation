/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.openai.v1_1;

import com.openai.client.OpenAIClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.embeddings.CreateEmbeddingResponse;
import com.openai.models.embeddings.EmbeddingCreateParams;
import com.openai.models.responses.Response;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.lang.reflect.Method;

final class InstrumentedOpenAiClient extends DelegatingInvocationHandler<OpenAIClient> {

  private final Instrumenter<ChatCompletionRequest, ChatCompletion> chatInstrumenter;
  private final Instrumenter<EmbeddingCreateParams, CreateEmbeddingResponse> embeddingInstrumenter;
  private final Instrumenter<ResponseCreateRequest, Response> responsesInstrumenter;
  private final Logger eventLogger;
  private final boolean captureMessageContent;

  InstrumentedOpenAiClient(
      OpenAIClient delegate,
      Instrumenter<ChatCompletionRequest, ChatCompletion> chatInstrumenter,
      Instrumenter<EmbeddingCreateParams, CreateEmbeddingResponse> embeddingInstrumenter,
      Instrumenter<ResponseCreateRequest, Response> responsesInstrumenter,
      Logger eventLogger,
      boolean captureMessageContent) {
    super(delegate);
    this.chatInstrumenter = chatInstrumenter;
    this.embeddingInstrumenter = embeddingInstrumenter;
    this.responsesInstrumenter = responsesInstrumenter;
    this.eventLogger = eventLogger;
    this.captureMessageContent = captureMessageContent;
  }

  @Override
  protected Class<OpenAIClient> getProxyType() {
    return OpenAIClient.class;
  }

  @Override
  protected OpenAIClient wrap(OpenAIClient delegate) {
    return new InstrumentedOpenAiClient(
            delegate,
            chatInstrumenter,
            embeddingInstrumenter,
            responsesInstrumenter,
            eventLogger,
            captureMessageContent)
        .createProxy();
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    String methodName = method.getName();
    Class<?>[] parameterTypes = method.getParameterTypes();
    if (methodName.equals("chat") && parameterTypes.length == 0) {
      return new InstrumentedChatService(
              delegate.chat(), chatInstrumenter, eventLogger, captureMessageContent)
          .createProxy();
    }
    if (methodName.equals("responses") && parameterTypes.length == 0) {
      return new InstrumentedResponseService(delegate.responses(), responsesInstrumenter)
          .createProxy();
    }
    if (methodName.equals("embeddings") && parameterTypes.length == 0) {
      return new InstrumentedEmbeddingService(delegate.embeddings(), embeddingInstrumenter)
          .createProxy();
    }
    if (methodName.equals("async") && parameterTypes.length == 0) {
      return new InstrumentedOpenAiClientAsync(
              delegate.async(),
              chatInstrumenter,
              embeddingInstrumenter,
              responsesInstrumenter,
              eventLogger,
              captureMessageContent)
          .createProxy();
    }
    return super.invoke(proxy, method, args);
  }
}
