/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.sofarpc.v5_4;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alipay.sofa.rpc.config.ConsumerConfig;
import com.alipay.sofa.rpc.core.request.SofaRequest;
import com.alipay.sofa.rpc.core.response.SofaResponse;
import com.alipay.sofa.rpc.filter.FilterInvoker;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TracingFilterTest {

  @Mock Instrumenter<SofaRpcRequest, SofaResponse> instrumenter;
  @Mock FilterInvoker invoker;
  @Mock SofaRequest request;
  @Mock SofaResponse response;

  @Test
  @SuppressWarnings("rawtypes")
  void asyncResponseEndsWithStartedRequest() {
    Context context = Context.root();
    TracingFilter filter = new TracingFilter(instrumenter, true);
    when(instrumenter.shouldStart(any(), any())).thenReturn(true);
    when(instrumenter.start(any(), any())).thenReturn(context);
    when(request.isAsync()).thenReturn(true);
    when(invoker.invoke(request)).thenReturn(response);

    filter.invoke(invoker, request);

    ArgumentCaptor<SofaRpcRequest> requestCaptor = ArgumentCaptor.forClass(SofaRpcRequest.class);
    verify(instrumenter).start(any(), requestCaptor.capture());
    SofaRpcRequest startedRequest = requestCaptor.getValue();

    filter.onAsyncResponse((ConsumerConfig) null, request, response, null);

    verify(instrumenter).end(same(context), same(startedRequest), same(response), same(null));
  }
}