/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.openai.v1_1;

import com.openai.core.RequestOptions;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.services.blocking.chat.ChatCompletionService;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.lang.reflect.Method;

final class InstrumentedChatCompletionService
    extends DelegatingInvocationHandler<ChatCompletionService, InstrumentedChatCompletionService> {

  private final Instrumenter<ChatCompletionCreateParams, ChatCompletion> instrumenter;
  private final Logger eventLogger;
  private final boolean captureMessageContent;

  InstrumentedChatCompletionService(
      ChatCompletionService delegate,
      Instrumenter<ChatCompletionCreateParams, ChatCompletion> instrumenter,
      Logger eventLogger,
      boolean captureMessageContent) {
    super(delegate);
    this.instrumenter = instrumenter;
    this.eventLogger = eventLogger;
    this.captureMessageContent = captureMessageContent;
  }

  @Override
  protected Class<ChatCompletionService> getProxyType() {
    return ChatCompletionService.class;
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    String methodName = method.getName();
    Class<?>[] parameterTypes = method.getParameterTypes();

    if (methodName.equals("create")
        && parameterTypes.length >= 1
        && parameterTypes[0] == ChatCompletionCreateParams.class) {
      if (parameterTypes.length == 1) {
        return create((ChatCompletionCreateParams) args[0], RequestOptions.none());
      } else if (parameterTypes.length == 2 && parameterTypes[1] == RequestOptions.class) {
        return create((ChatCompletionCreateParams) args[0], (RequestOptions) args[1]);
      }
    }

    return super.invoke(proxy, method, args);
  }

  private ChatCompletion create(
      ChatCompletionCreateParams chatCompletionCreateParams, RequestOptions requestOptions) {

    Context parentCtx = Context.current();
    if (!instrumenter.shouldStart(parentCtx, chatCompletionCreateParams)) {
      return createWithLogs(chatCompletionCreateParams, requestOptions);
    }

    Context ctx = instrumenter.start(parentCtx, chatCompletionCreateParams);
    ChatCompletion completion;
    try (Scope ignored = ctx.makeCurrent()) {
      completion = createWithLogs(chatCompletionCreateParams, requestOptions);
    } catch (Throwable t) {
      instrumenter.end(ctx, chatCompletionCreateParams, null, t);
      throw t;
    }

    instrumenter.end(ctx, chatCompletionCreateParams, completion, null);
    return completion;
  }

  private ChatCompletion createWithLogs(
      ChatCompletionCreateParams chatCompletionCreateParams, RequestOptions requestOptions) {
    ChatCompletionEventsHelper.emitPromptLogEvents(
        eventLogger, chatCompletionCreateParams, captureMessageContent);
    ChatCompletion result = delegate.create(chatCompletionCreateParams, requestOptions);
    ChatCompletionEventsHelper.emitCompletionLogEvents(eventLogger, result, captureMessageContent);
    return result;
  }
}
