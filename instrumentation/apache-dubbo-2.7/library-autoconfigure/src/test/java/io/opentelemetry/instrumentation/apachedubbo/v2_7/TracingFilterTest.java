/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v2_7;

import static io.opentelemetry.instrumentation.testing.junit.rpc.SemconvRpcStabilityUtil.emitOldRpcSemconv;
import static io.opentelemetry.instrumentation.testing.junit.rpc.SemconvRpcStabilityUtil.emitPreviewRpcSemconv;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_METHOD;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SERVICE;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SYSTEM;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SYSTEM_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.RpcInvocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("deprecation") // RpcContext and legacy semconv
class TracingFilterTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Mock Invoker<Runnable> invoker;
  @Mock RpcInvocation invocation;
  @Mock Result result;

  private final DubboTelemetry telemetry = DubboTelemetry.create(testing.getOpenTelemetry());

  @BeforeEach
  void setUp() {
    RpcContext.removeContext();
    RpcContext.getContext().setUrl(new URL("dubbo", "localhost", 0));
    RpcContext.getContext().setFuture(null);
    Mockito.<Invoker<?>>when(invocation.getInvoker()).thenReturn(invoker);
    when(invocation.getMethodName()).thenReturn("run");
    when(invoker.getInterface()).thenReturn(Runnable.class);
  }

  @AfterEach
  void tearDown() {
    RpcContext.getContext().setFuture(null);
    RpcContext.removeContext();
  }

  @ParameterizedTest
  @MethodSource("resultFailures")
  void resultFailure(boolean clientSide, RuntimeException error, String errorType) {
    when(invoker.invoke(invocation)).thenReturn(result);
    when(result.getException()).thenReturn(error);

    Context parent = Context.current();
    assertThat(filter(clientSide).invoke(invoker, invocation)).isSameAs(result);
    assertThat(Context.current()).isSameAs(parent);

    assertSpan(clientSide, errorType, StatusData.error());
  }

  private static Stream<Arguments> resultFailures() {
    return IntStream.rangeClosed(1, 13)
        .boxed()
        .flatMap(
            code ->
                Stream.of(true, false)
                    .map(
                        clientSide ->
                            Arguments.of(
                                clientSide, new RpcException(code), Integer.toString(code))));
  }

  @ParameterizedTest
  @MethodSource("fallbackFailures")
  void fallbackFailure(boolean clientSide, RuntimeException error, String errorType) {
    when(invoker.invoke(invocation)).thenReturn(result);
    when(result.getException()).thenReturn(error);

    assertThat(filter(clientSide).invoke(invoker, invocation)).isSameAs(result);

    assertSpan(clientSide, errorType, StatusData.error());
  }

  private static Stream<Arguments> fallbackFailures() {
    return Stream.of(true, false)
        .flatMap(
            clientSide ->
                Stream.of(
                        new RpcException(RpcException.UNKNOWN_EXCEPTION),
                        new RpcException(-1),
                        new RpcException(Integer.MAX_VALUE),
                        new IllegalStateException("not an RPC code"))
                    .map(error -> Arguments.of(clientSide, error, error.getClass().getName())));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void thrownFailure(boolean clientSide) {
    RpcException error = new RpcException(RpcException.NETWORK_EXCEPTION);
    when(invoker.invoke(invocation)).thenThrow(error);

    Context parent = Context.current();
    assertThatThrownBy(() -> filter(clientSide).invoke(invoker, invocation)).isSameAs(error);
    assertThat(Context.current()).isSameAs(parent);

    assertSpan(clientSide, Integer.toString(RpcException.NETWORK_EXCEPTION), StatusData.error());
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void asynchronousFailure(boolean wrapped) {
    CompletableFuture<Object> future = new CompletableFuture<>();
    RpcContext.getContext().setFuture(future);
    when(invoker.invoke(invocation)).thenReturn(result);

    Context parent = Context.current();
    assertThat(telemetry.newClientFilter().invoke(invoker, invocation)).isSameAs(result);
    assertThat(Context.current()).isSameAs(parent);
    assertThat(testing.spans()).isEmpty();

    RpcException error = new RpcException(RpcException.TIMEOUT_EXCEPTION);
    future.completeExceptionally(wrapped ? new CompletionException(error) : error);

    assertSpan(true, Integer.toString(RpcException.TIMEOUT_EXCEPTION), StatusData.error());
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void success(boolean clientSide) {
    when(invoker.invoke(invocation)).thenReturn(result);

    assertThat(filter(clientSide).invoke(invoker, invocation)).isSameAs(result);

    assertSpan(clientSide, null, StatusData.unset());
  }

  private Filter filter(boolean clientSide) {
    return clientSide ? telemetry.newClientFilter() : telemetry.newServerFilter();
  }

  private static void assertSpan(boolean clientSide, String errorType, StatusData status) {
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("java.lang.Runnable/run")
                        .hasKind(clientSide ? SpanKind.CLIENT : SpanKind.SERVER)
                        .hasNoParent()
                        .hasStatus(status)
                        .hasAttributesSatisfyingExactly(
                            equalTo(RPC_SYSTEM, emitOldRpcSemconv() ? "apache_dubbo" : null),
                            equalTo(RPC_SYSTEM_NAME, emitPreviewRpcSemconv() ? "dubbo" : null),
                            equalTo(RPC_SERVICE, emitOldRpcSemconv() ? "java.lang.Runnable" : null),
                            equalTo(
                                RPC_METHOD,
                                emitPreviewRpcSemconv() ? "java.lang.Runnable/run" : "run"),
                            equalTo(SERVER_ADDRESS, clientSide ? "localhost" : null),
                            equalTo(ERROR_TYPE, emitPreviewRpcSemconv() ? errorType : null))));

    if (errorType != null && emitPreviewRpcSemconv()) {
      testing.waitAndAssertMetrics(
          "io.opentelemetry.apache-dubbo-2.7",
          metric ->
              metric
                  .hasName(clientSide ? "rpc.client.call.duration" : "rpc.server.call.duration")
                  .hasUnit("s")
                  .hasHistogramSatisfying(
                      histogram ->
                          histogram.hasPointsSatisfying(
                              point ->
                                  point.hasAttributesSatisfyingExactly(
                                      equalTo(RPC_SYSTEM_NAME, "dubbo"),
                                      equalTo(RPC_METHOD, "java.lang.Runnable/run"),
                                      equalTo(SERVER_ADDRESS, clientSide ? "localhost" : null),
                                      equalTo(ERROR_TYPE, errorType)))));
    }
  }
}
