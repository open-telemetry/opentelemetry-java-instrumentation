/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.openai.v1_1;

import com.openai.core.RequestOptions;
import com.openai.core.http.StreamResponse;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionChunk;
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

    switch (methodName) {
      case "create":
        if (parameterTypes.length >= 1 && parameterTypes[0] == ChatCompletionCreateParams.class) {
          if (parameterTypes.length == 1) {
            return create((ChatCompletionCreateParams) args[0], RequestOptions.none());
          } else if (parameterTypes.length == 2 && parameterTypes[1] == RequestOptions.class) {
            return create((ChatCompletionCreateParams) args[0], (RequestOptions) args[1]);
          }
        }
        break;
      case "createStreaming":
        if (parameterTypes.length >= 1 && parameterTypes[0] == ChatCompletionCreateParams.class) {
          if (parameterTypes.length == 1) {
            return createStreaming((ChatCompletionCreateParams) args[0], RequestOptions.none());
          } else if (parameterTypes.length == 2 && parameterTypes[1] == RequestOptions.class) {
            return createStreaming((ChatCompletionCreateParams) args[0], (RequestOptions) args[1]);
          }
        }
        break;
      default:
        // fallthrough
    }

    return super.invoke(proxy, method, args);
  }

  private ChatCompletion create(
      ChatCompletionCreateParams chatCompletionCreateParams, RequestOptions requestOptions) {
    Context parentContext = Context.current();
    if (!instrumenter.shouldStart(parentContext, chatCompletionCreateParams)) {
      return createWithLogs(parentContext, chatCompletionCreateParams, requestOptions);
    }

    Context context = instrumenter.start(parentContext, chatCompletionCreateParams);
    ChatCompletion completion;
    try (Scope ignored = context.makeCurrent()) {
      completion = createWithLogs(context, chatCompletionCreateParams, requestOptions);
    } catch (Throwable t) {
      instrumenter.end(context, chatCompletionCreateParams, null, t);
      throw t;
    }

    instrumenter.end(context, chatCompletionCreateParams, completion, null);
    return completion;
  }

  private ChatCompletion createWithLogs(
      Context context,
      ChatCompletionCreateParams chatCompletionCreateParams,
      RequestOptions requestOptions) {
    ChatCompletionEventsHelper.emitPromptLogEvents(
        context, eventLogger, chatCompletionCreateParams, captureMessageContent);
    ChatCompletion result = delegate.create(chatCompletionCreateParams, requestOptions);
    ChatCompletionEventsHelper.emitCompletionLogEvents(
        context, eventLogger, result, captureMessageContent);
    return result;
  }

  private StreamResponse<ChatCompletionChunk> createStreaming(
      ChatCompletionCreateParams chatCompletionCreateParams, RequestOptions requestOptions) {
    Context parentContext = Context.current();
    if (!instrumenter.shouldStart(parentContext, chatCompletionCreateParams)) {
      return createStreamingWithLogs(
          parentContext, chatCompletionCreateParams, requestOptions, false);
    }

    Context context = instrumenter.start(parentContext, chatCompletionCreateParams);
    try (Scope ignored = context.makeCurrent()) {
      return createStreamingWithLogs(context, chatCompletionCreateParams, requestOptions, true);
    } catch (Throwable t) {
      instrumenter.end(context, chatCompletionCreateParams, null, t);
      throw t;
    }
  }

  private StreamResponse<ChatCompletionChunk> createStreamingWithLogs(
      Context context,
      ChatCompletionCreateParams chatCompletionCreateParams,
      RequestOptions requestOptions,
      boolean newSpan) {
    ChatCompletionEventsHelper.emitPromptLogEvents(
        context, eventLogger, chatCompletionCreateParams, captureMessageContent);
    StreamResponse<ChatCompletionChunk> result =
        delegate.createStreaming(chatCompletionCreateParams, requestOptions);
    return new TracingStreamedResponse(
        result,
        new StreamListener(
            context,
            chatCompletionCreateParams,
            instrumenter,
            eventLogger,
            captureMessageContent,
            newSpan));
  }
}
