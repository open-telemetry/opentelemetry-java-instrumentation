/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.openai.v1_1;

import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.services.blocking.ChatService;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.lang.reflect.Method;

final class InstrumentedChatService
    extends DelegatingInvocationHandler<ChatService, InstrumentedChatService> {

  private final Instrumenter<ChatCompletionCreateParams, ChatCompletion> instrumenter;
  private final Logger eventLogger;
  private final boolean captureMessageContent;

  public InstrumentedChatService(
      ChatService delegate,
      Instrumenter<ChatCompletionCreateParams, ChatCompletion> instrumenter,
      Logger eventLogger,
      boolean captureMessageContent) {
    super(delegate);
    this.instrumenter = instrumenter;
    this.eventLogger = eventLogger;
    this.captureMessageContent = captureMessageContent;
  }

  @Override
  protected Class<ChatService> getProxyType() {
    return ChatService.class;
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    String methodName = method.getName();
    Class<?>[] parameterTypes = method.getParameterTypes();
    if (methodName.equals("completions") && parameterTypes.length == 0) {
      return new InstrumentedChatCompletionService(
              delegate.completions(), instrumenter, eventLogger, captureMessageContent)
          .createProxy();
    }
    return super.invoke(proxy, method, args);
  }
}
