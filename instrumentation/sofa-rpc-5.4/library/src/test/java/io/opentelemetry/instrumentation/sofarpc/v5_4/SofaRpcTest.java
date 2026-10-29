/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.sofarpc.v5_4;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.instrumentation.testing.util.InstrumentationScopeAssertions.hasScopeSchemaUrl;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_METHOD;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SYSTEM_NAME;
import static java.util.Collections.singletonList;

import com.alipay.sofa.rpc.common.RpcConstants;
import com.alipay.sofa.rpc.config.ConsumerConfig;
import com.alipay.sofa.rpc.core.exception.RpcErrorType;
import com.alipay.sofa.rpc.core.exception.SofaRpcException;
import com.alipay.sofa.rpc.core.request.SofaRequest;
import com.alipay.sofa.rpc.core.response.SofaResponse;
import com.alipay.sofa.rpc.filter.Filter;
import com.alipay.sofa.rpc.filter.FilterInvoker;
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
  @CsvSource({"false, false", "false, true", "true, false", "true, true"})
  void statusCodeIsEmitted(boolean async, boolean syntheticFallback) {
    String service = ErrorService.class.getName();
    String method = syntheticFallback ? "throwBusinessException" : "throwException";
    String errorType = Integer.toString(RpcErrorType.SERVER_UNDECLARED_ERROR);
    SofaRequest request = new SofaRequest();
    request.setInterfaceName(service);
    request.setMethodName(method);
    if (async) {
      request.setInvokeType(RpcConstants.INVOKER_TYPE_FUTURE);
    }

    SofaResponse response = new SofaResponse();
    Throwable exception = new SofaRpcException(RpcErrorType.SERVER_UNDECLARED_ERROR, "RPC error");
    if (syntheticFallback) {
      response.setErrorMsg("RPC error");
    } else if (!async) {
      response.setAppResponse(exception);
    }

    ConsumerConfig<?> config = new ConsumerConfig<>().setInterfaceId(service).setInJVM(false);
    FilterInvoker invoker =
        new FilterInvoker(null, null, config) {
          @Override
          public SofaResponse invoke(SofaRequest ignored) {
            return response;
          }
        };
    Filter filter = telemetry.newClientFilter();
    filter.invoke(invoker, request);
    if (async) {
      filter.onAsyncResponse(config, request, response, syntheticFallback ? null : exception);
    }

    String rpcMethod = service + "/" + method;
    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName(rpcMethod)
                            .hasKind(SpanKind.CLIENT)
                            .satisfies(hasScopeSchemaUrl(SchemaUrls.V1_44_0))
                            .hasStatus(StatusData.error())
                            .hasAttributesSatisfyingExactly(
                                equalTo(RPC_SYSTEM_NAME, "sofarpc"),
                                equalTo(RPC_METHOD, rpcMethod),
                                equalTo(ERROR_TYPE, errorType),
                                equalTo(
                                    stringKey("rpc.status_code"),
                                    !syntheticFallback ? errorType : null))));

    testing()
        .waitAndAssertMetrics(
            "io.opentelemetry.sofa-rpc-5.4",
            "rpc.client.call.duration",
            metrics ->
                metrics.anySatisfy(
                    metric ->
                        assertThat(metric)
                            .hasName("rpc.client.call.duration")
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
                                                    equalTo(ERROR_TYPE, errorType),
                                                    equalTo(
                                                        stringKey("rpc.status_code"),
                                                        syntheticFallback ? null : errorType))))));
  }
}
