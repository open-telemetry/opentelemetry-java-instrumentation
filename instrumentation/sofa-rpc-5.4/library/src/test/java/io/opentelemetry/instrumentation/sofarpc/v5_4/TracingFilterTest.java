/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.sofarpc.v5_4;

import static io.opentelemetry.instrumentation.testing.junit.rpc.SemconvRpcStabilityUtil.emitOldRpcSemconv;
import static io.opentelemetry.instrumentation.testing.junit.rpc.SemconvRpcStabilityUtil.emitPreviewRpcSemconv;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
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
import com.alipay.sofa.rpc.core.exception.SofaRpcRuntimeException;
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
        Arguments.of(false, RpcErrorType.SERVER_CLOSED, "101"),
        Arguments.of(false, RpcErrorType.SERVER_NOT_FOUND_INVOKER, "110"),
        Arguments.of(false, RpcErrorType.SERVER_SERIALIZE, "120"),
        Arguments.of(false, RpcErrorType.SERVER_DESERIALIZE, "130"),
        Arguments.of(false, RpcErrorType.SERVER_NETWORK, "150"),
        Arguments.of(false, RpcErrorType.SERVER_BIZ, "160"),
        Arguments.of(false, RpcErrorType.SERVER_FILTER, "170"),
        Arguments.of(false, RpcErrorType.SERVER_UNDECLARED_ERROR, "199"),
        Arguments.of(true, RpcErrorType.CLIENT_TIMEOUT, "200"),
        Arguments.of(true, RpcErrorType.CLIENT_ROUTER, "210"),
        Arguments.of(true, RpcErrorType.CLIENT_SERIALIZE, "220"),
        Arguments.of(true, RpcErrorType.CLIENT_DESERIALIZE, "230"),
        Arguments.of(true, RpcErrorType.CLIENT_NETWORK, "250"),
        Arguments.of(true, RpcErrorType.CLIENT_FILTER, "270"),
        Arguments.of(true, RpcErrorType.CLIENT_UNDECLARED_ERROR, "299"));
  }

  private static Stream<Arguments> errorTypeRangeCases() {
    return Stream.of(
        Arguments.of(false, 100),
        Arguments.of(true, 100),
        Arguments.of(false, 199),
        Arguments.of(true, 199),
        Arguments.of(false, 200),
        Arguments.of(true, 200),
        Arguments.of(false, 260),
        Arguments.of(true, 260),
        Arguments.of(false, 299),
        Arguments.of(true, 299));
  }

  private static Stream<Arguments> outOfRangeErrorTypes() {
    return Stream.of(
        Arguments.of(false, 99),
        Arguments.of(true, 99),
        Arguments.of(false, 300),
        Arguments.of(true, 300),
        Arguments.of(false, 999),
        Arguments.of(true, 999));
  }

  @ParameterizedTest
  @MethodSource("errorCategories")
  void thrownRpcException(boolean client, int category, String expectedErrorType) {
    SofaRpcException exception = new SofaRpcException(category, "request-specific message");
    when(invoker.invoke(request)).thenThrow(exception);

    assertThatThrownBy(() -> filter(client).invoke(invoker, request)).isSameAs(exception);
    assertThat(Context.current()).isSameAs(Context.root());

    assertTelemetry(client, expectedErrorType, true);
  }

  @ParameterizedTest
  @MethodSource("errorTypeRangeCases")
  void errorTypeRangeBoundaries(boolean client, int errorType) {
    SofaRpcException exception = new SofaRpcException(errorType, "request-specific message");
    when(invoker.invoke(request)).thenThrow(exception);

    assertThatThrownBy(() -> filter(client).invoke(invoker, request)).isSameAs(exception);

    assertTelemetry(client, Integer.toString(errorType), true);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void unknownRpcErrorTypeFallsBackToExceptionClass(boolean client) {
    SofaRpcException exception = new SofaRpcException(RpcErrorType.UNKNOWN, "private message");
    when(invoker.invoke(request)).thenThrow(exception);

    assertThatThrownBy(() -> filter(client).invoke(invoker, request)).isSameAs(exception);

    assertTelemetry(client, SofaRpcException.class.getName(), true);
  }

  @ParameterizedTest
  @MethodSource("outOfRangeErrorTypes")
  void unsupportedRpcErrorTypeFallsBackToExceptionClass(boolean client, int errorType) {
    SofaRpcException exception = new SofaRpcException(errorType, "private message");
    when(invoker.invoke(request)).thenThrow(exception);

    assertThatThrownBy(() -> filter(client).invoke(invoker, request)).isSameAs(exception);

    assertTelemetry(client, SofaRpcException.class.getName(), true);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void unrelatedExceptionFallsBackToExceptionClass(boolean client) {
    SofaRpcRuntimeException exception = new SofaRpcRuntimeException("private message");
    when(invoker.invoke(request)).thenThrow(exception);

    assertThatThrownBy(() -> filter(client).invoke(invoker, request)).isSameAs(exception);

    assertTelemetry(client, SofaRpcRuntimeException.class.getName(), true);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void applicationExceptionResponse(boolean client) {
    SofaResponse response = new SofaResponse();
    response.setAppResponse(new SofaRpcException(RpcErrorType.SERVER_BIZ, "private message"));
    when(invoker.invoke(request)).thenReturn(response);

    assertThat(filter(client).invoke(invoker, request)).isSameAs(response);

    assertTelemetry(client, "160", true);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void responseError(boolean client) {
    SofaResponse response = new SofaResponse();
    response.setErrorMsg("private server message");
    when(invoker.invoke(request)).thenReturn(response);

    assertThat(filter(client).invoke(invoker, request)).isSameAs(response);

    assertTelemetry(client, "199", true);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void successfulResponse(boolean client) {
    SofaResponse response = new SofaResponse();
    response.setAppResponse("success");
    when(invoker.invoke(request)).thenReturn(response);

    assertThat(filter(client).invoke(invoker, request)).isSameAs(response);

    assertTelemetry(client, null, false);
  }

  private static Stream<Arguments> asyncCompletions() {
    return Stream.of(
        Arguments.of(new SofaRpcException(RpcErrorType.CLIENT_TIMEOUT, "private message"), "200"),
        Arguments.of(
            new SofaRpcRuntimeException("private message"),
            SofaRpcRuntimeException.class.getName()),
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

    assertTelemetry(true, expectedErrorType, exception != null);
  }

  private Filter filter(boolean client) {
    return client ? telemetry.newClientFilter() : telemetry.newServerFilter();
  }

  @SuppressWarnings("deprecation") // using deprecated semconv
  private static void assertTelemetry(boolean client, String errorType, boolean failed) {
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

    String oldMetricName = client ? "rpc.client.duration" : "rpc.server.duration";
    if (emitOldRpcSemconv()) {
      testing.waitAndAssertMetrics(
          "io.opentelemetry.sofa-rpc-5.4",
          oldMetricName,
          metrics ->
              metrics.anySatisfy(
                  metric ->
                      assertThat(metric)
                          .hasUnit("ms")
                          .hasHistogramSatisfying(
                              histogram ->
                                  histogram.hasPointsSatisfying(
                                      point ->
                                          point.hasAttributesSatisfyingExactly(
                                              equalTo(RPC_SYSTEM, "sofarpc"),
                                              equalTo(RPC_SERVICE, "example.Service"),
                                              equalTo(RPC_METHOD, "call"))))));
    }

    String stableMetricName = client ? "rpc.client.call.duration" : "rpc.server.call.duration";
    if (emitPreviewRpcSemconv()) {
      testing.waitAndAssertMetrics(
          "io.opentelemetry.sofa-rpc-5.4",
          stableMetricName,
          metrics ->
              metrics.anySatisfy(
                  metric ->
                      assertThat(metric)
                          .hasUnit("s")
                          .hasHistogramSatisfying(
                              histogram ->
                                  histogram.hasPointsSatisfying(
                                      point ->
                                          point.hasAttributesSatisfyingExactly(
                                              equalTo(RPC_SYSTEM_NAME, "sofarpc"),
                                              equalTo(RPC_METHOD, "example.Service/call"),
                                              equalTo(ERROR_TYPE, errorType))))));
    }
  }
}
