/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v2_7;

import static io.opentelemetry.instrumentation.testing.junit.rpc.SemconvRpcStabilityUtil.emitPreviewRpcSemconv;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.apachedubbo.v2_7.api.HelloService;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.rpc.AppResponse;
import org.apache.dubbo.rpc.AsyncRpcResult;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcInvocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class TracingFilterTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Test
  void asyncProviderFailureAfterReturn() {
    CompletableFuture<AppResponse> future = new CompletableFuture<>();
    IllegalStateException failure = new IllegalStateException("failure");
    invokeAsyncProvider(future);

    assertThat(testing.waitForTraces(0)).isEmpty();

    future.completeExceptionally(failure);

    assertFailure(failure);
  }

  @Test
  void asyncProviderFailureBeforeReturn() {
    CompletableFuture<AppResponse> future = new CompletableFuture<>();
    IllegalStateException failure = new IllegalStateException("failure");
    future.completeExceptionally(failure);

    invokeAsyncProvider(future);

    assertFailure(failure);
  }

  @Test
  void asyncProviderResponseFailureAfterReturn() {
    CompletableFuture<AppResponse> future = new CompletableFuture<>();
    IllegalStateException failure = new IllegalStateException("failure");
    invokeAsyncProvider(future);

    future.complete(new AppResponse(failure));

    assertFailure(failure);
  }

  @Test
  @SuppressWarnings("deprecation") // RpcContext.getContext()
  void asyncProviderTelemetryFailureDoesNotAffectResult() {
    AtomicInteger endCalls = new AtomicInteger();
    Filter filter =
        DubboTelemetry.builder(testing.getOpenTelemetry())
            .addAttributesExtractor(
                new AttributesExtractor<DubboRequest, Result>() {
                  @Override
                  public void onStart(
                      AttributesBuilder attributes, Context parentContext, DubboRequest request) {}

                  @Override
                  public void onEnd(
                      AttributesBuilder attributes,
                      Context context,
                      DubboRequest request,
                      Result response,
                      Throwable error) {
                    endCalls.incrementAndGet();
                    throw new IllegalStateException("telemetry failure");
                  }
                })
            .build()
            .newServerFilter();
    CompletableFuture<AppResponse> future = new CompletableFuture<>();
    AsyncRpcResult result = invokeAsyncProvider(future, filter);
    RpcContext completionContext = RpcContext.getContext();

    try {
      future.complete(new AppResponse("success"));

      assertThat(result.getResponseFuture().join().getValue()).isEqualTo("success");
      assertThat(RpcContext.getContext()).isSameAs(completionContext);
      assertThat(endCalls).hasValue(1);
    } finally {
      RpcContext.removeContext();
      RpcContext.removeServerContext();
    }
  }

  private static void assertFailure(Throwable failure) {
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(HelloService.class.getName() + "/hello")
                        .hasKind(SpanKind.SERVER)
                        .hasStatus(StatusData.error())
                        .hasAttributesSatisfying(
                            equalTo(
                                ERROR_TYPE,
                                emitPreviewRpcSemconv() ? failure.getClass().getName() : null))));
  }

  private static AsyncRpcResult invokeAsyncProvider(CompletableFuture<AppResponse> future) {
    return invokeAsyncProvider(
        future, DubboTelemetry.create(testing.getOpenTelemetry()).newServerFilter());
  }

  @SuppressWarnings("deprecation") // RpcContext.getContext()
  private static AsyncRpcResult invokeAsyncProvider(
      CompletableFuture<AppResponse> future, Filter filter) {
    URL url = new URL("dubbo", "localhost", 20880, HelloService.class.getName());
    RpcInvocation invocation = new RpcInvocation();
    invocation.setMethodName("hello");
    invocation.setParameterTypes(new Class<?>[] {String.class});
    invocation.setArguments(new Object[] {"request"});
    AsyncRpcResult result = new AsyncRpcResult(future, invocation);
    Invoker<HelloService> invoker = new AsyncInvoker(url, result);
    RpcContext.getContext().setUrl(url);
    invocation.setInvoker(invoker);

    try {
      assertThat(filter.invoke(invoker, invocation)).isSameAs(result);
      return result;
    } finally {
      RpcContext.removeContext();
    }
  }

  private static final class AsyncInvoker implements Invoker<HelloService> {

    private final URL url;
    private final Result result;

    private AsyncInvoker(URL url, Result result) {
      this.url = url;
      this.result = result;
    }

    @Override
    public Class<HelloService> getInterface() {
      return HelloService.class;
    }

    @Override
    public URL getUrl() {
      return url;
    }

    @Override
    public boolean isAvailable() {
      return true;
    }

    @Override
    public void destroy() {}

    @Override
    public Result invoke(Invocation invocation) {
      return result;
    }
  }
}
