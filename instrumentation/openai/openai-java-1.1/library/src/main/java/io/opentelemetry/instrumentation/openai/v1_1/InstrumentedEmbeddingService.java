/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.openai.v1_1;

import com.openai.core.RequestOptions;
import com.openai.models.embeddings.CreateEmbeddingResponse;
import com.openai.models.embeddings.EmbeddingCreateParams;
import com.openai.services.blocking.EmbeddingService;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.lang.reflect.Method;

final class InstrumentedEmbeddingService
    extends DelegatingInvocationHandler<EmbeddingService, InstrumentedEmbeddingService> {

  private final Instrumenter<EmbeddingCreateParams, CreateEmbeddingResponse> instrumenter;

  public InstrumentedEmbeddingService(
      EmbeddingService delegate,
      Instrumenter<EmbeddingCreateParams, CreateEmbeddingResponse> instrumenter) {
    super(delegate);
    this.instrumenter = instrumenter;
  }

  @Override
  protected Class<EmbeddingService> getProxyType() {
    return EmbeddingService.class;
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    String methodName = method.getName();
    Class<?>[] parameterTypes = method.getParameterTypes();

    if (methodName.equals("create")
        && parameterTypes.length >= 1
        && parameterTypes[0] == EmbeddingCreateParams.class) {
      if (parameterTypes.length == 1) {
        return create((EmbeddingCreateParams) args[0], RequestOptions.none());
      } else if (parameterTypes.length == 2 && parameterTypes[1] == RequestOptions.class) {
        return create((EmbeddingCreateParams) args[0], (RequestOptions) args[1]);
      }
    }

    return super.invoke(proxy, method, args);
  }

  private CreateEmbeddingResponse create(
      EmbeddingCreateParams request, RequestOptions requestOptions) {
    Context parentContext = Context.current();
    if (!instrumenter.shouldStart(parentContext, request)) {
      return delegate.create(request, requestOptions);
    }

    Context context = instrumenter.start(parentContext, request);
    CreateEmbeddingResponse response;
    try (Scope ignored = context.makeCurrent()) {
      response = delegate.create(request, requestOptions);
    } catch (Throwable t) {
      instrumenter.end(context, request, null, t);
      throw t;
    }

    instrumenter.end(context, request, response, null);
    return response;
  }
}
