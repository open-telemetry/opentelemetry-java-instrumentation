/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.openai.v1_1;

import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.services.async.ChatServiceAsync;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.instrumentation.api.incubator.semconv.genai.CaptureMessageOptions;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.lang.reflect.Method;

final class InstrumentedChatServiceAsync
    extends DelegatingInvocationHandler<ChatServiceAsync, InstrumentedChatServiceAsync> {

  private final Instrumenter<ChatCompletionCreateParams, ChatCompletion> instrumenter;
  private final Logger eventLogger;
  private final CaptureMessageOptions captureMessageOptions;

  InstrumentedChatServiceAsync(
      ChatServiceAsync delegate,
      Instrumenter<ChatCompletionCreateParams, ChatCompletion> instrumenter,
      Logger eventLogger,
      CaptureMessageOptions captureMessageOptions) {
    super(delegate);
    this.instrumenter = instrumenter;
    this.eventLogger = eventLogger;
    this.captureMessageOptions = captureMessageOptions;
  }

  @Override
  protected Class<ChatServiceAsync> getProxyType() {
    return ChatServiceAsync.class;
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    String methodName = method.getName();
    Class<?>[] parameterTypes = method.getParameterTypes();
    if (methodName.equals("completions") && parameterTypes.length == 0) {
      return new InstrumentedChatCompletionServiceAsync(
              delegate.completions(), instrumenter, eventLogger, captureMessageOptions)
          .createProxy();
    }
    return super.invoke(proxy, method, args);
  }
}
