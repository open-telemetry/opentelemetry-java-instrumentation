/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.sofarpc.v5_4;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.instrumentation.api.internal.SemconvExceptionSignal.emitExceptionAsLogs;
import static io.opentelemetry.instrumentation.api.internal.SemconvExceptionSignal.emitExceptionAsSpanEvents;
import static io.opentelemetry.instrumentation.testing.util.InstrumentationScopeAssertions.hasScopeSchemaUrl;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_METHOD;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SYSTEM_NAME;
import static java.util.Collections.singletonList;

import com.alipay.sofa.rpc.common.RemotingConstants;
import com.alipay.sofa.rpc.common.RpcConstants;
import com.alipay.sofa.rpc.config.AbstractInterfaceConfig;
import com.alipay.sofa.rpc.config.ConsumerConfig;
import com.alipay.sofa.rpc.config.ProviderConfig;
import com.alipay.sofa.rpc.core.exception.RpcErrorType;
import com.alipay.sofa.rpc.core.exception.SofaRpcException;
import com.alipay.sofa.rpc.core.request.SofaRequest;
import com.alipay.sofa.rpc.core.response.SofaResponse;
import com.alipay.sofa.rpc.filter.Filter;
import com.alipay.sofa.rpc.filter.FilterInvoker;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.sofarpc.v5_4.api.ErrorService;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.semconv.SchemaUrls;
import java.util.List;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SofaRpcTest extends AbstractSofaRpcTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  private final SofaRpcTelemetry telemetry = SofaRpcTelemetry.create(testing.getOpenTelemetry());

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  @Override
  protected List<Filter> clientFilters() {
    return singletonList(telemetry.newClientFilter());
  }

  @Override
  protected List<Filter> serverFilters() {
    return singletonList(telemetry.newServerFilter());
  }

  @ParameterizedTest
  @CsvSource({
    "false, CLIENT, SUCCESS",
    "true, CLIENT, SUCCESS",
    "false, CLIENT, ERROR_FLAG",
    "true, CLIENT, ERROR_FLAG",
    "false, CLIENT, ERROR_MESSAGE",
    "true, CLIENT, ERROR_MESSAGE",
    "false, CLIENT, RPC_EXCEPTION",
    "true, CLIENT, RPC_EXCEPTION",
    "false, CLIENT, APPLICATION_EXCEPTION",
    "true, CLIENT, APPLICATION_EXCEPTION",
    "true, CLIENT, CALLBACK_EXCEPTION",
    "false, SERVER, SUCCESS",
    "false, SERVER, ERROR_FLAG",
    "false, SERVER, ERROR_MESSAGE",
    "false, SERVER, RPC_EXCEPTION",
    "false, SERVER, APPLICATION_EXCEPTION"
  })
  @SuppressWarnings("rawtypes")
  void responseOutcome(boolean async, Side side, ResponseType responseType) {
    String service = ErrorService.class.getName();
    String method = "throwException";
    SofaRequest request = new SofaRequest();
    request.setInterfaceName(service);
    request.setMethodName(method);
    if (async) {
      request.setInvokeType(RpcConstants.INVOKER_TYPE_FUTURE);
    }

    SofaResponse response = new SofaResponse();
    response.addResponseProp(
        RemotingConstants.HEAD_RESPONSE_ERROR,
        responseType == ResponseType.SUCCESS || responseType == ResponseType.ERROR_MESSAGE
            ? "false"
            : "true");
    Throwable callbackException = null;
    Throwable actualException = null;
    String errorType = null;
    String statusCode = null;
    switch (responseType) {
      case SUCCESS:
        response.setAppResponse("success");
        break;
      case ERROR_FLAG:
        errorType = "_OTHER";
        break;
      case ERROR_MESSAGE:
        response.setErrorMsg("RPC error");
        errorType = "_OTHER";
        break;
      case RPC_EXCEPTION:
        actualException = new SofaRpcException(RpcErrorType.SERVER_BUSY, "RPC error");
        response.setAppResponse(actualException);
        errorType = Integer.toString(RpcErrorType.SERVER_BUSY);
        statusCode = errorType;
        break;
      case APPLICATION_EXCEPTION:
        actualException = new IllegalStateException("application error");
        response.setAppResponse(actualException);
        errorType = IllegalStateException.class.getName();
        break;
      case CALLBACK_EXCEPTION:
        response.setAppResponse(new IllegalStateException("response error"));
        callbackException = new SofaRpcException(RpcErrorType.CLIENT_TIMEOUT, "callback error");
        actualException = callbackException;
        errorType = Integer.toString(RpcErrorType.CLIENT_TIMEOUT);
        statusCode = errorType;
        break;
    }

    AbstractInterfaceConfig<?, ?> config =
        side == Side.CLIENT
            ? new ConsumerConfig<>().setInterfaceId(service).setInJVM(false)
            : new ProviderConfig<>().setInterfaceId(service);
    FilterInvoker invoker =
        new FilterInvoker(null, null, config) {
          @Override
          public SofaResponse invoke(SofaRequest ignored) {
            return response;
          }
        };
    Filter filter = side == Side.CLIENT ? telemetry.newClientFilter() : telemetry.newServerFilter();
    assertThat(filter.invoke(invoker, request)).isSameAs(response);
    if (async) {
      filter.onAsyncResponse((ConsumerConfig) config, request, response, callbackException);
    }

    String rpcMethod = service + "/" + method;
    String expectedErrorType = errorType;
    Throwable expectedException = actualException;
    String expectedStatusCode = statusCode;
    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName(rpcMethod)
                            .hasKind(side == Side.CLIENT ? SpanKind.CLIENT : SpanKind.SERVER)
                            .satisfies(hasScopeSchemaUrl(SchemaUrls.V1_44_0))
                            .hasStatus(
                                expectedErrorType == null ? StatusData.unset() : StatusData.error())
                            .hasException(emitExceptionAsSpanEvents() ? expectedException : null)
                            .hasAttributesSatisfyingExactly(
                                equalTo(RPC_SYSTEM_NAME, "sofarpc"),
                                equalTo(RPC_METHOD, rpcMethod),
                                equalTo(ERROR_TYPE, expectedErrorType),
                                equalTo(stringKey("rpc.status_code"), expectedStatusCode))));

    testing()
        .waitAndAssertMetrics(
            "io.opentelemetry.sofa-rpc-5.4",
            side == Side.CLIENT ? "rpc.client.call.duration" : "rpc.server.call.duration",
            metrics ->
                metrics.anySatisfy(
                    metric ->
                        assertThat(metric)
                            .hasName(
                                side == Side.CLIENT
                                    ? "rpc.client.call.duration"
                                    : "rpc.server.call.duration")
                            .hasUnit("s")
                            .satisfies(hasScopeSchemaUrl(SchemaUrls.V1_44_0))
                            .hasHistogramSatisfying(
                                histogram ->
                                    histogram.hasPointsSatisfying(
                                        point ->
                                            point
                                                .hasBucketBoundaries(
                                                    0.0001, 0.0005, 0.001, 0.005, 0.01, 0.05, 0.1,
                                                    0.5, 1.0, 5.0, 10.0, 50.0, 100.0)
                                                .hasAttributesSatisfyingExactly(
                                                    equalTo(RPC_SYSTEM_NAME, "sofarpc"),
                                                    equalTo(RPC_METHOD, rpcMethod),
                                                    equalTo(ERROR_TYPE, expectedErrorType),
                                                    equalTo(
                                                        stringKey("rpc.status_code"),
                                                        expectedStatusCode))))));
    if (emitExceptionAsLogs()) {
      if (expectedException == null) {
        testing().waitAndAssertLogRecords();
      } else {
        testing()
            .waitAndAssertLogRecords(
                logRecord ->
                    logRecord
                        .hasSeverity(side == Side.CLIENT ? Severity.WARN : Severity.ERROR)
                        .hasEventName(
                            side == Side.CLIENT
                                ? "rpc.client.call.exception"
                                : "rpc.server.call.exception")
                        .hasException(expectedException));
      }
    }
  }

  private enum Side {
    CLIENT,
    SERVER
  }

  private enum ResponseType {
    SUCCESS,
    ERROR_FLAG,
    ERROR_MESSAGE,
    RPC_EXCEPTION,
    APPLICATION_EXCEPTION,
    CALLBACK_EXCEPTION
  }
}
