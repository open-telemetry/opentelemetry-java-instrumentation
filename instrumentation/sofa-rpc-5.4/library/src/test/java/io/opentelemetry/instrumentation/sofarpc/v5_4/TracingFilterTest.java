/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.sofarpc.v5_4;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.alipay.sofa.rpc.common.RpcConstants;
import com.alipay.sofa.rpc.config.ConsumerConfig;
import com.alipay.sofa.rpc.core.request.SofaRequest;
import com.alipay.sofa.rpc.core.response.SofaResponse;
import com.alipay.sofa.rpc.filter.Filter;
import com.alipay.sofa.rpc.filter.FilterInvoker;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.test.utils.GcUtils;
import java.lang.ref.WeakReference;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TracingFilterTest {

  @Mock private Instrumenter<SofaRpcRequest, SofaResponse> instrumenter;

  private ConsumerConfig<?> consumerConfig;
  private SofaRequest request;
  private SofaResponse response;
  private FilterInvoker invoker;
  private TracingFilter filter;

  @BeforeEach
  void setUp() {
    filter = new TracingFilter(instrumenter, true);
    consumerConfig = new ConsumerConfig<>();
    consumerConfig.setInterfaceId(Runnable.class.getName());
    request = new SofaRequest().setInvokeType(RpcConstants.INVOKER_TYPE_FUTURE);
    response = new SofaResponse();
    Filter terminalFilter =
        new Filter() {
          @Override
          public SofaResponse invoke(FilterInvoker invoker, SofaRequest request) {
            return response;
          }
        };
    invoker = new FilterInvoker(terminalFilter, null, consumerConfig);
  }

  @Test
  void completesAsyncRequestOnlyOnce() {
    startAsyncRequest();

    filter.onAsyncResponse(consumerConfig, request, response, null);
    filter.onAsyncResponse(consumerConfig, request, response, null);

    ArgumentCaptor<SofaRpcRequest> startRequestCaptor =
        ArgumentCaptor.forClass(SofaRpcRequest.class);
    verify(instrumenter).start(any(), startRequestCaptor.capture());
    ArgumentCaptor<SofaRpcRequest> endRequestCaptor = ArgumentCaptor.forClass(SofaRpcRequest.class);
    verify(instrumenter)
        .end(eq(Context.root()), endRequestCaptor.capture(), same(response), isNull());
    assertThat(endRequestCaptor.getValue())
        .isNotSameAs(startRequestCaptor.getValue())
        .isEqualTo(startRequestCaptor.getValue());
    assertThat(request.getRequestProps()).isNull();
  }

  @Test
  void concurrentCompletionsEndOnlyOnce() throws Exception {
    startAsyncRequest();

    int completionCount = 16;
    ExecutorService executor = Executors.newFixedThreadPool(completionCount);
    CountDownLatch ready = new CountDownLatch(completionCount);
    CountDownLatch start = new CountDownLatch(1);
    try {
      Future<?>[] futures = new Future<?>[completionCount];
      for (int i = 0; i < completionCount; i++) {
        futures[i] =
            executor.submit(
                () -> {
                  ready.countDown();
                  start.await();
                  filter.onAsyncResponse(consumerConfig, request, response, null);
                  return null;
                });
      }

      ready.await();
      start.countDown();
      for (Future<?> future : futures) {
        future.get();
      }
    } finally {
      executor.shutdownNow();
    }

    verify(instrumenter, times(1)).end(any(), any(), any(), any());
    assertThat(request.getRequestProps()).isNull();
  }

  @Test
  void publicApiCompletionFallsBackToLibraryFilter() {
    startAsyncRequest();

    SofaRpcTelemetry.completeAsyncResponse(request, response, null);

    verify(instrumenter, times(1)).end(eq(Context.root()), any(), same(response), isNull());
  }

  @Test
  void callbackBeforeInvokeReturnsCompletesRequest() {
    when(instrumenter.shouldStart(any(), any())).thenReturn(true);
    when(instrumenter.start(any(), any())).thenReturn(Context.root());
    Filter callbackBeforeReturnFilter =
        new Filter() {
          @Override
          public SofaResponse invoke(FilterInvoker invoker, SofaRequest request) {
            filter.onAsyncResponse(consumerConfig, request, response, null);
            return response;
          }
        };

    filter.invoke(new FilterInvoker(callbackBeforeReturnFilter, null, consumerConfig), request);

    verify(instrumenter, times(1)).end(any(), any(), same(response), isNull());
    assertThat(request.getRequestProps()).isNull();
  }

  @Test
  void callbackCompletionAndLaterInvokeFailureEndOnlyOnce() {
    IllegalStateException invokeFailure = new IllegalStateException("invoke failed");
    when(instrumenter.shouldStart(any(), any())).thenReturn(true);
    when(instrumenter.start(any(), any())).thenReturn(Context.root());
    Filter callbackThenFailureFilter =
        new Filter() {
          @Override
          public SofaResponse invoke(FilterInvoker invoker, SofaRequest request) {
            filter.onAsyncResponse(consumerConfig, request, response, null);
            throw invokeFailure;
          }
        };

    assertThatThrownBy(
            () ->
                filter.invoke(
                    new FilterInvoker(callbackThenFailureFilter, null, consumerConfig), request))
        .isSameAs(invokeFailure);

    verify(instrumenter, times(1)).end(any(), any(), same(response), isNull());
    assertThat(request.getRequestProps()).isNull();
  }

  @Test
  void invokeFailureAndLaterCallbackEndOnlyOnce() {
    IllegalStateException invokeFailure = new IllegalStateException("invoke failed");
    when(instrumenter.shouldStart(any(), any())).thenReturn(true);
    when(instrumenter.start(any(), any())).thenReturn(Context.root());
    Filter failureFilter =
        new Filter() {
          @Override
          public SofaResponse invoke(FilterInvoker invoker, SofaRequest request) {
            throw invokeFailure;
          }
        };

    assertThatThrownBy(
            () -> filter.invoke(new FilterInvoker(failureFilter, null, consumerConfig), request))
        .isSameAs(invokeFailure);
    filter.onAsyncResponse(consumerConfig, request, response, null);

    verify(instrumenter, times(1)).end(any(), any(), isNull(), same(invokeFailure));
    assertThat(request.getRequestProps()).isNull();
  }

  @Test
  void asyncStateDoesNotModifyRequestProperties() {
    request.addRequestProp("existing", "value");

    startAsyncRequest();
    filter.onAsyncResponse(consumerConfig, request, response, null);

    assertThat(request.getRequestProps())
        .containsOnlyKeys("existing")
        .containsEntry("existing", "value");
  }

  @Test
  void asyncStateDoesNotRetainRequestCarrier() throws Exception {
    WeakReference<SofaRequest> requestReference = startUncompletedAsyncRequest();

    GcUtils.awaitGc(requestReference, Duration.ofSeconds(10));
  }

  @Test
  void explicitCallbackErrorWinsOverResponseError() {
    IllegalStateException callbackError = new IllegalStateException("callback failed");
    response.setAppResponse(new IllegalArgumentException("application failed"));
    startAsyncRequest();

    filter.onAsyncResponse(consumerConfig, request, response, callbackError);

    verify(instrumenter).end(any(), any(), any(), same(callbackError));
  }

  @Test
  void extractsApplicationError() {
    IllegalArgumentException applicationError = new IllegalArgumentException("application failed");
    response.setAppResponse(applicationError);
    startAsyncRequest();

    filter.onAsyncResponse(consumerConfig, request, response, null);

    verify(instrumenter).end(any(), any(), any(), same(applicationError));
  }

  @Test
  void completionAfterSynchronousRequestIsNoOp() {
    request.setInvokeType(RpcConstants.INVOKER_TYPE_SYNC);
    when(instrumenter.shouldStart(any(), any())).thenReturn(true);
    when(instrumenter.start(any(), any())).thenReturn(Context.root());

    filter.invoke(invoker, request);
    filter.onAsyncResponse(consumerConfig, request, response, null);

    verify(instrumenter, times(1)).end(any(), any(), any(), any());
  }

  @Test
  void localRequestAndCompletionAreNoOp() {
    consumerConfig.setInJVM(true);

    filter.invoke(invoker, request);
    filter.onAsyncResponse(consumerConfig, request, response, null);

    verifyNoInteractions(instrumenter);
  }

  @Test
  void completionWithoutAsyncStateIsNoOp() {
    filter.onAsyncResponse(consumerConfig, request, response, null);

    verifyNoInteractions(instrumenter);
  }

  private void startAsyncRequest() {
    when(instrumenter.shouldStart(any(), any())).thenReturn(true);
    when(instrumenter.start(any(), any())).thenReturn(Context.root());
    filter.invoke(invoker, request);
  }

  private WeakReference<SofaRequest> startUncompletedAsyncRequest() throws Exception {
    when(instrumenter.shouldStart(any(), any())).thenReturn(true);
    when(instrumenter.start(any(), any())).thenReturn(Context.root());
    AtomicReference<WeakReference<SofaRequest>> requestReference = new AtomicReference<>();
    Thread requestThread =
        new Thread(
            () -> {
              SofaRequest uncompletedRequest =
                  new SofaRequest().setInvokeType(RpcConstants.INVOKER_TYPE_FUTURE);
              filter.invoke(invoker, uncompletedRequest);
              requestReference.set(new WeakReference<>(uncompletedRequest));
            });
    requestThread.start();
    requestThread.join();
    reset((Object) instrumenter);
    return requestReference.get();
  }
}
