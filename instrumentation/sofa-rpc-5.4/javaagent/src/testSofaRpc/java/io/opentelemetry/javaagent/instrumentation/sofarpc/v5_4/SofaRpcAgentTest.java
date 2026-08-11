/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.sofarpc.v5_4;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldRpcSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableRpcSemconv;
import static io.opentelemetry.instrumentation.testing.GlobalTraceUtil.runWithSpan;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;

import com.alipay.sofa.rpc.common.RpcConstants;
import com.alipay.sofa.rpc.config.ConsumerConfig;
import com.alipay.sofa.rpc.core.request.SofaRequest;
import com.alipay.sofa.rpc.core.response.SofaResponse;
import com.alipay.sofa.rpc.filter.Filter;
import com.alipay.sofa.rpc.filter.FilterInvoker;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.sofarpc.v5_4.AbstractSofaRpcTest;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class SofaRpcAgentTest extends AbstractSofaRpcTest {

  private static final String CLIENT_FILTER_CLASS_NAME =
      "io.opentelemetry.javaagent.instrumentation.sofarpc.v5_4.OpenTelemetryClientFilter";

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  @Override
  protected boolean hasPeerService() {
    return true;
  }

  @Override
  protected String genericMethodName() {
    return "$invoke";
  }

  @Test
  void staticAsyncCompletionProducesSpanAndMetric() throws Exception {
    AsyncTestCall call = startAsyncTestCall("staticOnlySuccess", "ok");

    call.completeStatically(null);

    assertSingleAsyncClientSpanAndMetric("staticOnlySuccess", false);
  }

  @Test
  void staticAsyncExceptionProducesSpanAndMetric() throws Exception {
    IllegalStateException callbackError = new IllegalStateException("callback failed");
    AsyncTestCall call = startAsyncTestCall("staticOnlyException", null);

    call.completeStatically(callbackError);

    assertSingleAsyncClientSpanAndMetric("staticOnlyException", true);
  }

  @Test
  void standardAndStaticAsyncCompletionEndOnlyOnce() throws Exception {
    AsyncTestCall call = startAsyncTestCall("completeOnce", "ok");

    ExecutorService executor = Executors.newFixedThreadPool(2);
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    try {
      Future<?> standardCompletion =
          executor.submit(
              () -> {
                ready.countDown();
                start.await();
                call.filter.onAsyncResponse(call.consumerConfig, call.request, call.response, null);
                return null;
              });
      Future<?> staticCompletion =
          executor.submit(
              () -> {
                ready.countDown();
                start.await();
                call.completeStatically(null);
                return null;
              });

      ready.await();
      start.countDown();
      standardCompletion.get();
      staticCompletion.get();
    } finally {
      executor.shutdownNow();
    }

    assertSingleAsyncClientSpanAndMetric("completeOnce", false);
  }

  @Test
  void staticCompletionWithoutAsyncStateIsNoOp() throws Exception {
    Method completeAsyncResponse = getStaticCompletionMethod();

    completeAsyncResponse.invoke(
        null, new ConsumerConfig<>(), new SofaRequest(), new SofaResponse(), null);
  }

  private static AsyncTestCall startAsyncTestCall(String methodName, Object appResponse)
      throws Exception {
    Class<?> filterClass = getClientFilterClass();
    Filter filter = (Filter) filterClass.getConstructor().newInstance();
    ConsumerConfig<Object> consumerConfig = new ConsumerConfig<>();
    consumerConfig.setInterfaceId(AsyncService.class.getName());
    SofaRequest request = new SofaRequest();
    request.setInterfaceName("test.AsyncService");
    request.setMethodName(methodName);
    request.setInvokeType(RpcConstants.INVOKER_TYPE_FUTURE);
    SofaResponse response = new SofaResponse();
    response.setAppResponse(appResponse);
    Filter terminalFilter =
        new Filter() {
          @Override
          public SofaResponse invoke(FilterInvoker invoker, SofaRequest request) {
            return response;
          }
        };
    FilterInvoker invoker = new FilterInvoker(terminalFilter, null, consumerConfig);

    runWithSpan("parent", () -> filter.invoke(invoker, request));
    return new AsyncTestCall(
        filter, getStaticCompletionMethod(), consumerConfig, request, response);
  }

  private static Method getStaticCompletionMethod() throws Exception {
    return getClientFilterClass()
        .getMethod(
            "completeAsyncResponse",
            ConsumerConfig.class,
            SofaRequest.class,
            SofaResponse.class,
            Throwable.class);
  }

  private static Class<?> getClientFilterClass() throws Exception {
    // Loading ExtensionLoader triggers the agent's helper and resource injection for SOFA RPC.
    Class.forName("com.alipay.sofa.rpc.ext.ExtensionLoader");
    return Class.forName(CLIENT_FILTER_CLASS_NAME);
  }

  private static void assertSingleAsyncClientSpanAndMetric(String methodName, boolean error) {
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span -> {
                  span.hasName("test.AsyncService/" + methodName)
                      .hasKind(SpanKind.CLIENT)
                      .hasParent(trace.getSpan(0));
                  if (error) {
                    span.hasStatus(StatusData.error());
                  }
                }));

    String metricName = emitStableRpcSemconv() ? "rpc.client.call.duration" : "rpc.client.duration";
    if (emitOldRpcSemconv() || emitStableRpcSemconv()) {
      testing.waitAndAssertMetrics(
          "io.opentelemetry.sofa-rpc-5.4",
          metricName,
          metrics ->
              metrics.anySatisfy(
                  metric ->
                      assertThat(metric)
                          .hasHistogramSatisfying(
                              histogram ->
                                  histogram.hasPointsSatisfying(point -> point.hasCount(1)))));
    }
  }

  private static final class AsyncTestCall {

    private final Filter filter;
    private final Method completeAsyncResponse;
    private final ConsumerConfig<Object> consumerConfig;
    private final SofaRequest request;
    private final SofaResponse response;

    private AsyncTestCall(
        Filter filter,
        Method completeAsyncResponse,
        ConsumerConfig<Object> consumerConfig,
        SofaRequest request,
        SofaResponse response) {
      this.filter = filter;
      this.completeAsyncResponse = completeAsyncResponse;
      this.consumerConfig = consumerConfig;
      this.request = request;
      this.response = response;
    }

    private void completeStatically(Throwable exception) throws Exception {
      completeAsyncResponse.invoke(null, consumerConfig, request, response, exception);
    }
  }

  private interface AsyncService {}
}
