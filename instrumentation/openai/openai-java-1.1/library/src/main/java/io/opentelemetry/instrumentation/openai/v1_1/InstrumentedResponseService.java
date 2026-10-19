/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.openai.v1_1;

import com.openai.core.RequestOptions;
import com.openai.core.http.StreamResponse;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseStreamEvent;
import com.openai.services.blocking.ResponseService;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.lang.reflect.Method;

final class InstrumentedResponseService extends DelegatingInvocationHandler<ResponseService> {

  private final Instrumenter<ResponseCreateRequest, Response> instrumenter;

  InstrumentedResponseService(
      ResponseService delegate, Instrumenter<ResponseCreateRequest, Response> instrumenter) {
    super(delegate);
    this.instrumenter = instrumenter;
  }

  @Override
  protected Class<ResponseService> getProxyType() {
    return ResponseService.class;
  }

  @Override
  protected ResponseService wrap(ResponseService delegate) {
    return new InstrumentedResponseService(delegate, instrumenter).createProxy();
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

  private Object createStructured(Object structuredParams, RequestOptions requestOptions)
      throws Throwable {
    Response response =
        create(ResponseServiceOverloads.rawParams(structuredParams), requestOptions);
    return ResponseServiceOverloads.toStructuredResponse(structuredParams, response);
  }

  private Response create(ResponseCreateParams params, RequestOptions requestOptions) {
    ResponseCreateRequest request = ResponseCreateRequest.create(params);
    Context parentContext = Context.current();
    if (!instrumenter.shouldStart(parentContext, request)) {
      return delegate.create(params, requestOptions);
    }

    Context context = instrumenter.start(parentContext, request);
    Response response;
    try (Scope ignored = context.makeCurrent()) {
      response = delegate.create(params, requestOptions);
    } catch (Throwable t) {
      instrumenter.end(context, request, null, t);
      throw t;
    }

    instrumenter.end(context, request, response, null);
    return response;
  }

  private StreamResponse<ResponseStreamEvent> createStreaming(
      ResponseCreateParams params, RequestOptions requestOptions) {
    ResponseCreateRequest request = ResponseCreateRequest.createStreaming(params);
    Context parentContext = Context.current();
    if (!instrumenter.shouldStart(parentContext, request)) {
      return delegate.createStreaming(params, requestOptions);
    }

    Context context = instrumenter.start(parentContext, request);
    StreamResponse<ResponseStreamEvent> result;
    try (Scope ignored = context.makeCurrent()) {
      result = delegate.createStreaming(params, requestOptions);
    } catch (Throwable t) {
      instrumenter.end(context, request, null, t);
      throw t;
    }
    return new TracingStreamedResponse<>(
        result, new ResponseStreamListener(context, request, instrumenter));
  }
}
