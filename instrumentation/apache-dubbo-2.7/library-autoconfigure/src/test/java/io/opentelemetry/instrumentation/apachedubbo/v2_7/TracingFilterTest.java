/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v2_7;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldRpcSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitPreviewRpcSemconv;
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
    RpcContext.getContext().setUrl(new URL("dubbo", "localhost", 0));
    Mockito.<Invoker<?>>when(invocation.getInvoker()).thenReturn(invoker);
    when(invocation.getMethodName()).thenReturn("run");
    when(invoker.getInterface()).thenReturn(Runnable.class);
  }

  @AfterEach
  void tearDown() {
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
    return Stream.of(
            Arguments.of(new RpcException(RpcException.NETWORK_EXCEPTION), "1"),
            Arguments.of(new RpcException(RpcException.TIMEOUT_EXCEPTION), "2"),
            Arguments.of(new RpcException(RpcException.BIZ_EXCEPTION), "3"),
            Arguments.of(new RpcException(RpcException.FORBIDDEN_EXCEPTION), "4"),
            Arguments.of(new RpcException(RpcException.SERIALIZATION_EXCEPTION), "5"),
            Arguments.of(new RpcException(RpcException.NO_INVOKER_AVAILABLE_AFTER_FILTER), "6"),
            Arguments.of(
                new RpcException(RpcException.UNKNOWN_EXCEPTION), RpcException.class.getName()),
            Arguments.of(new RpcException(-1), RpcException.class.getName()),
            Arguments.of(new RpcException(Integer.MAX_VALUE), RpcException.class.getName()),
            Arguments.of(
                new IllegalStateException("not an RPC code"),
                IllegalStateException.class.getName()))
        .flatMap(
            failure ->
                Stream.of(true, false)
                    .map(
                        clientSide ->
                            Arguments.of(clientSide, failure.get()[0], failure.get()[1])));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void thrownFailure(boolean clientSide) {
    RpcException error = new RpcException(RpcException.NETWORK_EXCEPTION);
    when(invoker.invoke(invocation)).thenThrow(error);

    Context parent = Context.current();
    assertThatThrownBy(() -> filter(clientSide).invoke(invoker, invocation)).isSameAs(error);
    assertThat(Context.current()).isSameAs(parent);

    assertSpan(clientSide, "1", StatusData.error());
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
    CompletableFuture.runAsync(
            () -> future.completeExceptionally(wrapped ? new CompletionException(error) : error))
        .join();

    assertSpan(true, "2", StatusData.error());
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
  }
}
