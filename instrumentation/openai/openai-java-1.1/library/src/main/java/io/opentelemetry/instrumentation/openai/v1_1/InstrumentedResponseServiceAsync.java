/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.openai.v1_1;

import com.openai.core.RequestOptions;
import com.openai.core.http.AsyncStreamResponse;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseStreamEvent;
import com.openai.services.async.ResponseServiceAsync;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;

final class InstrumentedResponseServiceAsync
    extends DelegatingInvocationHandler<ResponseServiceAsync> {

  private final Instrumenter<ResponseCreateRequest, Response> instrumenter;

  InstrumentedResponseServiceAsync(
      ResponseServiceAsync delegate, Instrumenter<ResponseCreateRequest, Response> instrumenter) {
    super(delegate);
    this.instrumenter = instrumenter;
  }

  @Override
  protected Class<ResponseServiceAsync> getProxyType() {
    return ResponseServiceAsync.class;
  }

  @Override
  protected ResponseServiceAsync wrap(ResponseServiceAsync delegate) {
    return new InstrumentedResponseServiceAsync(delegate, instrumenter).createProxy();
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    String methodName = method.getName();
    Class<?>[] parameterTypes = method.getParameterTypes();

    switch (methodName) {
      case "create":
        if (parameterTypes.length == 0
            || (parameterTypes.length == 1 && parameterTypes[0] == RequestOptions.class)) {
          ResponseCreateParams emptyParams = ResponseServiceOverloads.emptyParams();
          if (emptyParams != null) {
            return create(
                emptyParams,
                parameterTypes.length == 0 ? RequestOptions.none() : (RequestOptions) args[0]);
          }
        } else if (parameterTypes[0] == ResponseCreateParams.class) {
          if (parameterTypes.length == 1) {
            return create((ResponseCreateParams) args[0], RequestOptions.none());
          } else if (parameterTypes.length == 2 && parameterTypes[1] == RequestOptions.class) {
            return create((ResponseCreateParams) args[0], (RequestOptions) args[1]);
          }
        } else if (ResponseServiceOverloads.isStructuredParams(parameterTypes[0])) {
          if (parameterTypes.length == 1) {
            return createStructured(args[0], RequestOptions.none());
          } else if (parameterTypes.length == 2 && parameterTypes[1] == RequestOptions.class) {
            return createStructured(args[0], (RequestOptions) args[1]);
          }
        }
        break;
      case "createStreaming":
        if (parameterTypes.length == 0
            || (parameterTypes.length == 1 && parameterTypes[0] == RequestOptions.class)) {
          ResponseCreateParams emptyParams = ResponseServiceOverloads.emptyParams();
          if (emptyParams != null) {
            return createStreaming(
                emptyParams,
                parameterTypes.length == 0 ? RequestOptions.none() : (RequestOptions) args[0]);
          }
        } else if (parameterTypes[0] == ResponseCreateParams.class) {
          if (parameterTypes.length == 1) {
            return createStreaming((ResponseCreateParams) args[0], RequestOptions.none());
          } else if (parameterTypes.length == 2 && parameterTypes[1] == RequestOptions.class) {
            return createStreaming((ResponseCreateParams) args[0], (RequestOptions) args[1]);
          }
        } else if (ResponseServiceOverloads.isStructuredParams(parameterTypes[0])) {
          if (parameterTypes.length == 1) {
            return createStreaming(
                ResponseServiceOverloads.rawParams(args[0]), RequestOptions.none());
          } else if (parameterTypes.length == 2 && parameterTypes[1] == RequestOptions.class) {
            return createStreaming(
                ResponseServiceOverloads.rawParams(args[0]), (RequestOptions) args[1]);
          }
        }
        break;
      default:
        // fallthrough
    }

    return super.invoke(proxy, method, args);
  }

  private CompletableFuture<Object> createStructured(
      Object structuredParams, RequestOptions requestOptions) throws Throwable {
    CompletableFuture<Response> future =
        create(ResponseServiceOverloads.rawParams(structuredParams), requestOptions);
    return future.thenApply(
        response -> {
          try {
            return ResponseServiceOverloads.toStructuredResponse(structuredParams, response);
          } catch (RuntimeException | Error e) {
            throw e;
          } catch (Throwable t) {
            throw new IllegalStateException(t);
          }
        });
  }

  private CompletableFuture<Response> create(
      ResponseCreateParams params, RequestOptions requestOptions) {
    ResponseCreateRequest request = ResponseCreateRequest.create(params);
    Context parentContext = Context.current();
    if (!instrumenter.shouldStart(parentContext, request)) {
      return delegate.create(params, requestOptions);
    }

    Context context = instrumenter.start(parentContext, request);
    CompletableFuture<Response> future;
    try (Scope ignored = context.makeCurrent()) {
      future = delegate.create(params, requestOptions);
    } catch (Throwable t) {
      instrumenter.end(context, request, null, t);
      throw t;
    }

    future = future.whenComplete((res, t) -> instrumenter.end(context, request, res, t));
    return CompletableFutureWrapper.wrap(future, parentContext);
  }

  private AsyncStreamResponse<ResponseStreamEvent> createStreaming(
      ResponseCreateParams params, RequestOptions requestOptions) {
    ResponseCreateRequest request = ResponseCreateRequest.createStreaming(params);
    Context parentContext = Context.current();
    if (!instrumenter.shouldStart(parentContext, request)) {
      return delegate.createStreaming(params, requestOptions);
    }

    Context context = instrumenter.start(parentContext, request);
    AsyncStreamResponse<ResponseStreamEvent> result;
    try (Scope ignored = context.makeCurrent()) {
      result = delegate.createStreaming(params, requestOptions);
    } catch (Throwable t) {
      instrumenter.end(context, request, null, t);
      throw t;
    }
    return new TracingAsyncStreamedResponse<>(
        result, new ResponseStreamListener(context, request, instrumenter));
  }
}
