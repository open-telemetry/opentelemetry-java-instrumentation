/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.sofarpc.v5_4;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldRpcSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitPreviewRpcSemconv;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_METHOD;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SERVICE;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SYSTEM;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SYSTEM_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.alipay.sofa.rpc.common.RpcConstants;
import com.alipay.sofa.rpc.config.ConsumerConfig;
import com.alipay.sofa.rpc.core.exception.RpcErrorType;
import com.alipay.sofa.rpc.core.exception.SofaRpcException;
import com.alipay.sofa.rpc.core.request.SofaRequest;
import com.alipay.sofa.rpc.core.response.SofaResponse;
import com.alipay.sofa.rpc.filter.Filter;
import com.alipay.sofa.rpc.filter.FilterInvoker;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

@SuppressWarnings("deprecation") // using deprecated semconv
class TracingFilterTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  private final SofaRpcTelemetry telemetry = SofaRpcTelemetry.create(testing.getOpenTelemetry());
  private final FilterInvoker invoker = mock(FilterInvoker.class);
  private final SofaRequest request = new SofaRequest();

  TracingFilterTest() {
    request.setInterfaceName("example.Service");
    request.setMethodName("call");
  }

  private static Stream<Arguments> errorCategories() {
    return Stream.of(
        Arguments.of(false, RpcErrorType.SERVER_BUSY, "100"),
        Arguments.of(false, RpcErrorType.SERVER_SERIALIZE, "120"),
        Arguments.of(false, RpcErrorType.SERVER_NETWORK, "150"),
        Arguments.of(true, RpcErrorType.CLIENT_TIMEOUT, "200"),
        Arguments.of(true, RpcErrorType.CLIENT_SERIALIZE, "220"),
        Arguments.of(true, RpcErrorType.CLIENT_NETWORK, "250"));
  }

  @ParameterizedTest
  @MethodSource("errorCategories")
  void thrownRpcException(boolean client, int category, String expectedErrorType) {
    SofaRpcException exception = new SofaRpcException(category, "request-specific message");
    when(invoker.invoke(request)).thenThrow(exception);

    assertThatThrownBy(() -> filter(client).invoke(invoker, request)).isSameAs(exception);
    assertThat(Context.current()).isSameAs(Context.root());

    assertSpan(client, expectedErrorType, true);
  }

  @ParameterizedTest
  @MethodSource("errorCategories")
  void applicationRpcException(boolean client, int category, String expectedErrorType) {
    SofaResponse response = new SofaResponse();
    response.setAppResponse(new SofaRpcException(category, "different request-specific message"));
    when(invoker.invoke(request)).thenReturn(response);

    assertThat(filter(client).invoke(invoker, request)).isSameAs(response);

    assertSpan(client, expectedErrorType, true);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void responseError(boolean client) {
    SofaResponse response = new SofaResponse();
    response.setErrorMsg("request-specific server error");
    when(invoker.invoke(request)).thenReturn(response);

    assertThat(filter(client).invoke(invoker, request)).isSameAs(response);

    assertSpan(client, "199", true);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void applicationExceptionFallback(boolean client) {
    SofaResponse response = new SofaResponse();
    response.setAppResponse(new IllegalStateException("application failure"));
    when(invoker.invoke(request)).thenReturn(response);

    assertThat(filter(client).invoke(invoker, request)).isSameAs(response);

    assertSpan(client, IllegalStateException.class.getName(), true);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void successfulResponse(boolean client) {
    SofaResponse response = new SofaResponse();
    response.setAppResponse("success");
    when(invoker.invoke(request)).thenReturn(response);

    assertThat(filter(client).invoke(invoker, request)).isSameAs(response);

    assertSpan(client, null, false);
  }

  private static Stream<Arguments> asyncCompletions() {
    return Stream.of(
        Arguments.of(new SofaRpcException(RpcErrorType.CLIENT_TIMEOUT, "timeout"), "200"),
        Arguments.of(new SofaRpcException(RpcErrorType.CLIENT_NETWORK, "network"), "250"),
        Arguments.of(
            new IllegalStateException("application failure"),
            IllegalStateException.class.getName()),
        Arguments.of(null, null));
  }

  @ParameterizedTest
  @MethodSource("asyncCompletions")
  void asyncCompletion(Throwable exception, String expectedErrorType) {
    request.setInvokeType(RpcConstants.INVOKER_TYPE_CALLBACK);
    SofaResponse response = new SofaResponse();
    when(invoker.invoke(request)).thenReturn(response);
    Filter filter = filter(true);

    assertThat(filter.invoke(invoker, request)).isSameAs(response);
    assertThat(testing.spans()).isEmpty();
    assertThat(Context.current()).isSameAs(Context.root());

    filter.onAsyncResponse(new ConsumerConfig<>(), request, response, exception);

    assertSpan(true, expectedErrorType, exception != null);
  }

  private Filter filter(boolean client) {
    return client ? telemetry.newClientFilter() : telemetry.newServerFilter();
  }

  private static void assertSpan(boolean client, String errorType, boolean failed) {
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("example.Service/call")
                        .hasKind(client ? SpanKind.CLIENT : SpanKind.SERVER)
                        .hasStatus(failed ? StatusData.error() : StatusData.unset())
                        .hasAttributesSatisfyingExactly(
                            equalTo(RPC_SYSTEM, emitOldRpcSemconv() ? "sofarpc" : null),
                            equalTo(RPC_SERVICE, emitOldRpcSemconv() ? "example.Service" : null),
                            equalTo(RPC_SYSTEM_NAME, emitPreviewRpcSemconv() ? "sofarpc" : null),
                            equalTo(
                                RPC_METHOD,
                                emitPreviewRpcSemconv() ? "example.Service/call" : "call"),
                            equalTo(ERROR_TYPE, emitPreviewRpcSemconv() ? errorType : null))));
  }
}
