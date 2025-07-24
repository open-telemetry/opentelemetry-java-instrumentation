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
    Context parentCtx = Context.current();
    if (!instrumenter.shouldStart(parentCtx, chatCompletionCreateParams)) {
      return createWithLogs(parentCtx, chatCompletionCreateParams, requestOptions);
    }

    Context ctx = instrumenter.start(parentCtx, chatCompletionCreateParams);
    CompletableFuture<ChatCompletion> future;
    try (Scope ignored = ctx.makeCurrent()) {
      future = createWithLogs(ctx, chatCompletionCreateParams, requestOptions);
    } catch (Throwable t) {
      instrumenter.end(ctx, chatCompletionCreateParams, null, t);
      throw t;
    }

    future =
        future.whenComplete((res, t) -> instrumenter.end(ctx, chatCompletionCreateParams, res, t));
    return CompletableFutureWrapper.wrap(future, ctx);
  }

  private CompletableFuture<ChatCompletion> createWithLogs(
      Context ctx,
      ChatCompletionCreateParams chatCompletionCreateParams,
      RequestOptions requestOptions) {
    ChatCompletionEventsHelper.emitPromptLogEvents(
        ctx, eventLogger, chatCompletionCreateParams, captureMessageContent);
    CompletableFuture<ChatCompletion> future =
        delegate.create(chatCompletionCreateParams, requestOptions);
    future.thenAccept(
        r ->
            ChatCompletionEventsHelper.emitCompletionLogEvents(
                ctx, eventLogger, r, captureMessageContent));
    return future;
  }

  private AsyncStreamResponse<ChatCompletionChunk> createStreaming(
      ChatCompletionCreateParams chatCompletionCreateParams, RequestOptions requestOptions) {
    Context parentCtx = Context.current();
    if (!instrumenter.shouldStart(parentCtx, chatCompletionCreateParams)) {
      return createStreamingWithLogs(parentCtx, chatCompletionCreateParams, requestOptions, false);
    }

    Context ctx = instrumenter.start(parentCtx, chatCompletionCreateParams);
    try (Scope ignored = ctx.makeCurrent()) {
      return createStreamingWithLogs(ctx, chatCompletionCreateParams, requestOptions, true);
    } catch (Throwable t) {
      instrumenter.end(ctx, chatCompletionCreateParams, null, t);
      throw t;
    }
  }

  private AsyncStreamResponse<ChatCompletionChunk> createStreamingWithLogs(
      Context ctx,
      ChatCompletionCreateParams chatCompletionCreateParams,
      RequestOptions requestOptions,
      boolean newSpan) {
    ChatCompletionEventsHelper.emitPromptLogEvents(
        ctx, eventLogger, chatCompletionCreateParams, captureMessageContent);
    AsyncStreamResponse<ChatCompletionChunk> result =
        delegate.createStreaming(chatCompletionCreateParams, requestOptions);
    return new TracingAsyncStreamedResponse(
        result,
        new StreamListener(
            ctx,
            chatCompletionCreateParams,
            instrumenter,
            eventLogger,
            captureMessageContent,
            newSpan));
  }
}
