/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.common.v2_7;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_LOCAL_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_LOCAL_PORT;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_TYPE;
import static io.opentelemetry.semconv.RpcAttributes.RPC_METHOD;
import static io.opentelemetry.semconv.RpcAttributes.RPC_METHOD_ORIGINAL;
import static io.opentelemetry.semconv.RpcAttributes.RPC_SYSTEM_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.apachedubbo.v2_7.api.HelloService;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.rpc.AppResponse;
import org.apache.dubbo.rpc.AsyncRpcResult;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcInvocation;
import org.apache.dubbo.rpc.service.GenericService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TracingFilterTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @ParameterizedTest
  @MethodSource("localEndpoints")
  @SuppressWarnings("deprecation") // RpcContext.getContext() and removeContext()
  void providerLocalEndpoint(
      String bindHost, boolean contextAddress, String expectedAddress, long expectedPort) {
    URL url =
        new URL("dubbo", "192.0.2.1", 20880, HelloService.class.getName())
            .addParameter("bind.port", 20881);
    if (bindHost != null) {
      url = url.addParameter("bind.ip", bindHost);
    }
    Invoker<?> invoker = mock(Invoker.class);
    when(invoker.getUrl()).thenReturn(url);
    when(invoker.getInterface()).thenAnswer(unused -> HelloService.class);
    RpcInvocation invocation = new RpcInvocation();
    invocation.setMethodName("hello");
    AppResponse result = new AppResponse("response");
    when(invoker.invoke(invocation)).thenReturn(result);
    Filter filter =
        new DubboInstrumenterBuilder(
                testing.getOpenTelemetry(), "io.opentelemetry.apache-dubbo-common-2.7")
            .build()
            .newServerFilterWithInvoker();
    try {
      if (contextAddress) {
        RpcContext.getContext().setLocalAddress(new InetSocketAddress("127.0.0.2", 20882));
      }
      if ("localhost".equals(bindHost) && !contextAddress) {
        assertThat(
                DubboRequest.createForInvoker(invocation, RpcContext.getContext(), invoker)
                    .localAddress())
            .isNotNull()
            .satisfies(address -> assertThat(address.isUnresolved()).isTrue());
      }
      assertThat(filter.invoke(invoker, invocation)).isSameAs(result);
      testing.waitAndAssertTraces(trace ->
          trace.hasSpansSatisfyingExactly(span ->
              span.hasKind(SpanKind.SERVER)
                  .hasAttributesSatisfying(
                      equalTo(NETWORK_LOCAL_ADDRESS, expectedAddress),
                      equalTo(NETWORK_LOCAL_PORT, expectedPort))));
    } finally {
      RpcContext.removeContext();
    }
  }

  private static Stream<Arguments> localEndpoints() {
    return Stream.of(
        argumentSet("IPv4 bind endpoint", "127.0.0.1", false, "127.0.0.1", 20881L),
        argumentSet("IPv6 bind endpoint", "2001:db8::1", false, "2001:db8:0:0:0:0:0:1", 20881L),
        argumentSet(
            "bracketed IPv6 bind endpoint", "[2001:db8::1]", false, "2001:db8:0:0:0:0:0:1", 20881L),
        argumentSet("hostname bind endpoint", "localhost", false, null, 20881L),
        argumentSet("invalid numeric bind endpoint", "999.1.1.1", false, null, 20881L),
        argumentSet("advertised endpoint without bind IP", null, false, "192.0.2.1", 20881L),
        argumentSet("context endpoint", "localhost", true, "127.0.0.2", 20882L));
  }

  @ParameterizedTest
  @MethodSource("providerMethods")
  @SuppressWarnings("deprecation") // RpcContext.getContext() and removeContext()
  void providerTracingBeforeContextAndGenericFilters(
      Class<?> service, String method, Object[] arguments, String expectedMethod, String original) {
    Invoker<?> invoker = mock(Invoker.class);
    when(invoker.getUrl()).thenReturn(new URL("dubbo", "127.0.0.1", 20880, service.getName()));
    when(invoker.getInterface()).thenAnswer(unused -> service);
    RpcInvocation invocation = new RpcInvocation();
    invocation.setMethodName(method);
    invocation.setArguments(arguments);
    AppResponse result = new AppResponse("response");
    when(invoker.invoke(invocation)).thenReturn(result);
    Filter filter =
        new DubboInstrumenterBuilder(
                testing.getOpenTelemetry(), "io.opentelemetry.apache-dubbo-common-2.7")
            .build()
            .newServerFilterWithInvoker();
    try {
      assertThat(
              DubboRequest.createForInvoker(invocation, RpcContext.getContext(), invoker)
                  .isUnknownService())
          .isFalse();
      assertThat(filter.invoke(invoker, invocation)).isSameAs(result);
      testing.waitAndAssertTraces(trace ->
          trace.hasSpansSatisfyingExactly(span ->
              span.hasName("_OTHER".equals(expectedMethod) ? "dubbo" : expectedMethod)
                  .hasKind(SpanKind.SERVER)
                  .hasAttributesSatisfyingExactly(
                      equalTo(RPC_SYSTEM_NAME, "dubbo"),
                      equalTo(RPC_METHOD, expectedMethod),
                      equalTo(RPC_METHOD_ORIGINAL, original),
                      equalTo(NETWORK_LOCAL_ADDRESS, "127.0.0.1"),
                      equalTo(NETWORK_LOCAL_PORT, 20880L),
                      equalTo(NETWORK_TYPE, "ipv4"))));
      testing.waitAndAssertMetrics(
          "io.opentelemetry.apache-dubbo-common-2.7",
          metric ->
              metric
                  .hasName("rpc.server.call.duration")
                  .hasHistogramPointsSatisfying(point ->
                      point.hasAttributesSatisfyingExactly(
                          equalTo(RPC_SYSTEM_NAME, "dubbo"), equalTo(RPC_METHOD, expectedMethod))));
    } finally {
      RpcContext.removeContext();
    }
  }

  private static Stream<Arguments> providerMethods() {
    String hello = HelloService.class.getName() + "/hello";
    String missing = HelloService.class.getName() + "/missing";
    String inherited = ChildService.class.getName() + "/inherited";
    String staticMethod = ChildService.class.getName() + "/utility";
    return Stream.of(
        argumentSet(
            "ordinary method", HelloService.class, "hello", new Object[] {"request"}, hello, null),
        argumentSet(
            "generic invocation",
            HelloService.class,
            "$invoke",
            genericArgs(" hello "),
            hello,
            null),
        argumentSet(
            "async generic invocation",
            HelloService.class,
            "$invokeAsync",
            genericArgs("hello"),
            hello,
            null),
        argumentSet(
            "unknown method",
            HelloService.class,
            "$invoke",
            genericArgs("missing"),
            "_OTHER",
            missing),
        argumentSet(
            "unknown async method",
            HelloService.class,
            "$invokeAsync",
            genericArgs("missing"),
            "_OTHER",
            missing),
        argumentSet(
            "blank generic method",
            HelloService.class,
            "$invoke",
            genericArgs(" "),
            "_OTHER",
            HelloService.class.getName() + "/"),
        argumentSet(
            "inherited method",
            ChildService.class,
            "$invoke",
            genericArgs("inherited"),
            inherited,
            null),
        argumentSet(
            "ordinary inherited method",
            ChildService.class,
            "inherited",
            new Object[0],
            inherited,
            null),
        argumentSet(
            "echo method",
            HelloService.class,
            "$echo",
            new Object[] {"request"},
            HelloService.class.getName() + "/$echo",
            null),
        argumentSet(
            "static method",
            ChildService.class,
            "$invoke",
            genericArgs("utility"),
            "_OTHER",
            staticMethod),
        argumentSet(
            "generic provider",
            GenericService.class,
            "$invoke",
            genericArgs("missing"),
            GenericService.class.getName() + "/$invoke",
            null),
        argumentSet(
            "generic async provider",
            GenericService.class,
            "$invokeAsync",
            genericArgs("missing"),
            GenericService.class.getName() + "/$invokeAsync",
            null),
        argumentSet(
            "non-generic unknown method",
            HelloService.class,
            "missing",
            genericArgs("missing"),
            "_OTHER",
            missing),
        argumentSet(
            "malformed generic arguments",
            HelloService.class,
            "$invoke",
            new Object[] {"missing"},
            HelloService.class.getName() + "/$invoke",
            null),
        argumentSet(
            "non-string generic method",
            HelloService.class,
            "$invoke",
            new Object[] {42, new String[0], new Object[0]},
            HelloService.class.getName() + "/$invoke",
            null));
  }

  private static Object[] genericArgs(String method) {
    return new Object[] {method, new String[] {String.class.getName()}, new Object[] {"request"}};
  }

  interface ParentService {
    void inherited();
  }

  interface ChildService extends ParentService {
    static void utility() {}
  }

  @Test
  void unknownServiceRetainsOriginalMethod() {
    DubboRequest request =
        DubboRequest.createForUnknownService(new RpcInvocation(), "unknown.Service/$invoke", null);
    DubboRpcAttributesGetter getter = new DubboRpcAttributesGetter(false);
    assertThat(getter.getRpcMethod(request)).isEqualTo("_OTHER");
    assertThat(getter.getRpcMethodOriginal(request)).isEqualTo("unknown.Service/$invoke");
  }

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
        new DubboInstrumenterBuilder(
                testing.getOpenTelemetry(), "io.opentelemetry.apache-dubbo-common-2.7")
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
    testing.waitAndAssertTraces(trace ->
        trace.hasSpansSatisfyingExactly(span ->
            span.hasName(HelloService.class.getName() + "/hello")
                .hasKind(SpanKind.SERVER)
                .hasStatus(StatusData.error())
                .hasAttributesSatisfying(equalTo(ERROR_TYPE, failure.getClass().getName()))));
  }

  private static AsyncRpcResult invokeAsyncProvider(CompletableFuture<AppResponse> future) {
    return invokeAsyncProvider(
        future,
        new DubboInstrumenterBuilder(
                testing.getOpenTelemetry(), "io.opentelemetry.apache-dubbo-common-2.7")
            .build()
            .newServerFilter());
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
