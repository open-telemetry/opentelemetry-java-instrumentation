/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.openai.v1_1;

import com.openai.client.OpenAIClientAsync;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.embeddings.CreateEmbeddingResponse;
import com.openai.models.embeddings.EmbeddingCreateParams;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.lang.reflect.Method;

final class InstrumentedOpenAiClientAsync
    extends DelegatingInvocationHandler<OpenAIClientAsync, InstrumentedOpenAiClientAsync> {

  private final Instrumenter<ChatCompletionCreateParams, ChatCompletion> chatInstrumenter;
  private final Instrumenter<EmbeddingCreateParams, CreateEmbeddingResponse> embeddingInstrumenter;
  private final Logger eventLogger;
  private final boolean captureMessageContent;

  InstrumentedOpenAiClientAsync(
      OpenAIClientAsync delegate,
      Instrumenter<ChatCompletionCreateParams, ChatCompletion> chatInstrumenter,
      Instrumenter<EmbeddingCreateParams, CreateEmbeddingResponse> embeddingInstrumenter,
      Logger eventLogger,
      boolean captureMessageContent) {
    super(delegate);
    this.chatInstrumenter = chatInstrumenter;
    this.embeddingInstrumenter = embeddingInstrumenter;
    this.eventLogger = eventLogger;
    this.captureMessageContent = captureMessageContent;
  }

  @Override
  protected Class<OpenAIClientAsync> getProxyType() {
    return OpenAIClientAsync.class;
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    String methodName = method.getName();
    Class<?>[] parameterTypes = method.getParameterTypes();
    if (methodName.equals("chat") && parameterTypes.length == 0) {
      return new InstrumentedChatServiceAsync(
              delegate.chat(), chatInstrumenter, eventLogger, captureMessageContent)
          .createProxy();
    }
    if (methodName.equals("embeddings") && parameterTypes.length == 0) {
      return new InstrumentedEmbeddingServiceAsync(delegate.embeddings(), embeddingInstrumenter)
          .createProxy();
    }
    if (methodName.equals("sync") && parameterTypes.length == 0) {
      return new InstrumentedOpenAiClient(
              delegate.sync(),
              chatInstrumenter,
              embeddingInstrumenter,
              eventLogger,
              captureMessageContent)
          .createProxy();
    }
    return super.invoke(proxy, method, args);
  }
}
