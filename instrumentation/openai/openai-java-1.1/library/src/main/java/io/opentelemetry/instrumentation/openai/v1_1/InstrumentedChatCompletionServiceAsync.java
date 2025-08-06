/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.openai.v1_1;

import com.openai.core.RequestOptions;
import com.openai.core.http.AsyncStreamResponse;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionChunk;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.services.async.chat.ChatCompletionServiceAsync;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;

final class InstrumentedChatCompletionServiceAsync
    extends DelegatingInvocationHandler<
        ChatCompletionServiceAsync, InstrumentedChatCompletionServiceAsync> {

  private final Instrumenter<ChatCompletionCreateParams, ChatCompletion> instrumenter;
  private final Logger eventLogger;
  private final boolean captureMessageContent;

  InstrumentedChatCompletionServiceAsync(
      ChatCompletionServiceAsync delegate,
      Instrumenter<ChatCompletionCreateParams, ChatCompletion> instrumenter,
      Logger eventLogger,
      boolean captureMessageContent) {
    super(delegate);
    this.instrumenter = instrumenter;
    this.eventLogger = eventLogger;
    this.captureMessageContent = captureMessageContent;
  }

  @Override
  protected Class<ChatCompletionServiceAsync> getProxyType() {
    return ChatCompletionServiceAsync.class;
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

  private CompletableFuture<ChatCompletion> create(
      ChatCompletionCreateParams chatCompletionCreateParams, RequestOptions requestOptions) {
    Context parentContext = Context.current();
    if (!instrumenter.shouldStart(parentContext, chatCompletionCreateParams)) {
      return createWithLogs(parentContext, chatCompletionCreateParams, requestOptions);
    }

    Context context = instrumenter.start(parentContext, chatCompletionCreateParams);
    CompletableFuture<ChatCompletion> future;
    try (Scope ignored = context.makeCurrent()) {
      future = createWithLogs(context, chatCompletionCreateParams, requestOptions);
    } catch (Throwable t) {
      instrumenter.end(context, chatCompletionCreateParams, null, t);
      throw t;
    }

    future =
        future.whenComplete(
            (res, t) -> instrumenter.end(context, chatCompletionCreateParams, res, t));
    return CompletableFutureWrapper.wrap(future, parentContext);
  }

  private CompletableFuture<ChatCompletion> createWithLogs(
      Context context,
      ChatCompletionCreateParams chatCompletionCreateParams,
      RequestOptions requestOptions) {
    ChatCompletionEventsHelper.emitPromptLogEvents(
        context, eventLogger, chatCompletionCreateParams, captureMessageContent);
    CompletableFuture<ChatCompletion> future =
        delegate.create(chatCompletionCreateParams, requestOptions);
    future.thenAccept(
        r ->
            ChatCompletionEventsHelper.emitCompletionLogEvents(
                context, eventLogger, r, captureMessageContent));
    return future;
  }

  private AsyncStreamResponse<ChatCompletionChunk> createStreaming(
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

  private AsyncStreamResponse<ChatCompletionChunk> createStreamingWithLogs(
      Context context,
      ChatCompletionCreateParams chatCompletionCreateParams,
      RequestOptions requestOptions,
      boolean newSpan) {
    ChatCompletionEventsHelper.emitPromptLogEvents(
        context, eventLogger, chatCompletionCreateParams, captureMessageContent);
    AsyncStreamResponse<ChatCompletionChunk> result =
        delegate.createStreaming(chatCompletionCreateParams, requestOptions);
    return new TracingAsyncStreamedResponse(
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
