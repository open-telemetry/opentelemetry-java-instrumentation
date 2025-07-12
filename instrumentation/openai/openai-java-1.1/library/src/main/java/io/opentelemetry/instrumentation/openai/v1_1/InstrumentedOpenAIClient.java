/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.openai.v1_1;

import com.openai.client.OpenAIClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.lang.reflect.Method;

@SuppressWarnings("IdentifierName") // Want to match library's convention
final class InstrumentedOpenAIClient
    extends DelegatingInvocationHandler<OpenAIClient, InstrumentedOpenAIClient> {

  private final Instrumenter<ChatCompletionCreateParams, ChatCompletion> chatInstrumenter;
  private final boolean captureMessageContent;

  InstrumentedOpenAIClient(
      OpenAIClient delegate,
      Instrumenter<ChatCompletionCreateParams, ChatCompletion> chatInstrumenter,
      boolean captureMessageContent) {
    super(delegate);
    this.chatInstrumenter = chatInstrumenter;
    this.captureMessageContent = captureMessageContent;
  }

  @Override
  protected Class<OpenAIClient> getProxyType() {
    return OpenAIClient.class;
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    String methodName = method.getName();
    Class<?>[] parameterTypes = method.getParameterTypes();
    if (methodName.equals("chat") && parameterTypes.length == 0) {
      return new InstrumentedChatService(delegate.chat(), chatInstrumenter, captureMessageContent)
          .createProxy();
    }
    return super.invoke(proxy, method, args);
  }
}
