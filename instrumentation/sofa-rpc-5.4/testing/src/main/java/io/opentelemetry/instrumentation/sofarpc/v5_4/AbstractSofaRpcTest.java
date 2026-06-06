/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.sofarpc.v5_4;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldRpcSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableRpcSemconv;
import static io.opentelemetry.instrumentation.testing.GlobalTraceUtil.runWithSpan;
import static io.opentelemetry.instrumentation.testing.junit.service.SemconvServiceStabilityUtil.maybeStablePeerService;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_TYPE;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_METHOD;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SERVICE;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SYSTEM;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SYSTEM_NAME;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.alipay.sofa.rpc.api.GenericService;
import com.alipay.sofa.rpc.api.future.SofaResponseFuture;
import com.alipay.sofa.rpc.common.RpcConstants;
import com.alipay.sofa.rpc.config.ApplicationConfig;
import com.alipay.sofa.rpc.config.ConsumerConfig;
import com.alipay.sofa.rpc.config.ProviderConfig;
import com.alipay.sofa.rpc.config.ServerConfig;
import com.alipay.sofa.rpc.context.RpcInternalContext;
import com.alipay.sofa.rpc.core.exception.SofaRpcException;
import com.alipay.sofa.rpc.core.exception.SofaRpcRuntimeException;
import com.alipay.sofa.rpc.core.exception.SofaTimeOutException;
import com.alipay.sofa.rpc.core.request.SofaRequest;
import com.alipay.sofa.rpc.core.response.SofaResponse;
import com.alipay.sofa.rpc.filter.Filter;
import com.alipay.sofa.rpc.filter.FilterInvoker;
import java.util.ArrayList;
import java.util.List;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.sofarpc.v5_4.api.ErrorService;
import io.opentelemetry.instrumentation.sofarpc.v5_4.api.HelloService;
import io.opentelemetry.instrumentation.sofarpc.v5_4.impl.ErrorServiceImpl;
import io.opentelemetry.instrumentation.sofarpc.v5_4.impl.HelloServiceImpl;
import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.trace.data.StatusData;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.AbstractStringAssert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

@SuppressWarnings("deprecation") // using deprecated semconv
public abstract class AbstractSofaRpcTest {

  protected abstract InstrumentationExtension testing();

  protected abstract boolean hasPeerService();

  protected List<Filter> clientFilters() {
    return emptyList();
  }

  protected List<Filter> serverFilters() {
    return emptyList();
  }

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  ConsumerConfig<GenericService> configureGenericClient(int port) {
    ConsumerConfig<GenericService> consumer = new ConsumerConfig<>();
    consumer
        .setInterfaceId(HelloService.class.getName())
        .setApplication(new ApplicationConfig().setAppName("sofa-rpc-test-consumer"))
        .setDirectUrl("bolt://127.0.0.1:" + port)
        .setRegister(false)
        .setTimeout(30000)
        .setGeneric(true);
    if (!clientFilters().isEmpty()) {
      consumer.setFilterRef(clientFilters());
    }
    return consumer;
  }

  ProviderConfig<HelloService> configureServer(int port) {
    ServerConfig serverConfig =
        new ServerConfig().setProtocol("bolt").setHost("127.0.0.1").setPort(port).setDaemon(false);

    ProviderConfig<HelloService> provider = new ProviderConfig<>();
    provider
        .setInterfaceId(HelloService.class.getName())
        .setRef(new HelloServiceImpl())
        .setApplication(new ApplicationConfig().setAppName("sofa-rpc-test-provider"))
        .setServer(serverConfig)
        .setRegister(false);
    if (!serverFilters().isEmpty()) {
      provider.setFilterRef(serverFilters());
    }
    return provider;
  }

  ProviderConfig<ErrorService> configureErrorServer(int port) {
    ServerConfig serverConfig =
        new ServerConfig().setProtocol("bolt").setHost("127.0.0.1").setPort(port).setDaemon(false);

    ProviderConfig<ErrorService> provider = new ProviderConfig<>();
    provider
        .setInterfaceId(ErrorService.class.getName())
        .setRef(new ErrorServiceImpl())
        .setApplication(new ApplicationConfig().setAppName("sofa-rpc-test-error-provider"))
        .setServer(serverConfig)
        .setRegister(false);
    if (!serverFilters().isEmpty()) {
      provider.setFilterRef(serverFilters());
    }
    return provider;
  }

  ConsumerConfig<ErrorService> configureErrorClient(int port) {
    ConsumerConfig<ErrorService> consumer = new ConsumerConfig<>();
    consumer
        .setInterfaceId(ErrorService.class.getName())
        .setApplication(new ApplicationConfig().setAppName("sofa-rpc-test-error-consumer"))
        .setDirectUrl("bolt://127.0.0.1:" + port)
        .setRegister(false)
        .setTimeout(30000);
    if (!clientFilters().isEmpty()) {
      consumer.setFilterRef(clientFilters());
    }
    return consumer;
  }

  @Test
  void testSofaRpcBase() {
    int port = PortUtils.findOpenPort();

    ProviderConfig<HelloService> providerConfig = configureServer(port);
    cleanup.deferCleanup(providerConfig::unExport);
    providerConfig.export();

    ConsumerConfig<GenericService> consumerConfig = configureGenericClient(port);
    cleanup.deferCleanup(consumerConfig::unRefer);
    GenericService genericService = consumerConfig.refer();

    Object response =
        runWithSpan(
            "parent",
            () ->
                genericService.$invoke(
                    "hello", new String[] {String.class.getName()}, new Object[] {"hello"}));

    assertThat(response).isEqualTo("hello");

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                    span ->
                        span.hasName("com.alipay.sofa.rpc.api.GenericService/$invoke")
                            .hasKind(SpanKind.CLIENT)
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfyingExactly(
                                equalTo(RPC_SYSTEM, emitOldRpcSemconv() ? "sofarpc" : null),
                                equalTo(
                                    RPC_SYSTEM_NAME, emitStableRpcSemconv() ? "sofarpc" : null),
                                equalTo(
                                    RPC_SERVICE,
                                    emitOldRpcSemconv() ? GenericService.class.getName() : null),
                                equalTo(
                                    RPC_METHOD,
                                    emitStableRpcSemconv()
                                        ? "com.alipay.sofa.rpc.api.GenericService/$invoke"
                                        : "$invoke"),
                                equalTo(
                                    maybeStablePeerService(),
                                    hasPeerService() ? "test-peer-service" : null),
                                equalTo(SERVER_ADDRESS, "127.0.0.1"),
                                satisfies(SERVER_PORT, val -> val.isInstanceOf(Long.class)),
                                satisfies(
                                    NETWORK_PEER_ADDRESS,
                                    AbstractSofaRpcTest::assertNetworkPeerAddress),
                                satisfies(
                                    NETWORK_PEER_PORT, AbstractSofaRpcTest::assertNetworkPeerPort),
                                satisfies(NETWORK_TYPE, AbstractSofaRpcTest::assertNetworkType)),
                    span ->
                        span.hasName(
                                "io.opentelemetry.instrumentation.sofarpc.v5_4.api.HelloService/hello")
                            .hasKind(SpanKind.SERVER)
                            .hasParent(trace.getSpan(1))
                            .hasAttributesSatisfyingExactly(
                                equalTo(RPC_SYSTEM, emitOldRpcSemconv() ? "sofarpc" : null),
                                equalTo(
                                    RPC_SYSTEM_NAME, emitStableRpcSemconv() ? "sofarpc" : null),
                                equalTo(
                                    RPC_SERVICE,
                                    emitOldRpcSemconv()
                                        ? "io.opentelemetry.instrumentation.sofarpc.v5_4.api.HelloService"
                                        : null),
                                equalTo(
                                    RPC_METHOD,
                                    emitStableRpcSemconv()
                                        ? "io.opentelemetry.instrumentation.sofarpc.v5_4.api.HelloService/hello"
                                        : "hello"),
                                satisfies(
                                    NETWORK_PEER_ADDRESS,
                                    AbstractSofaRpcTest::assertNetworkPeerAddress),
                                satisfies(
                                    NETWORK_PEER_PORT,
                                    AbstractSofaRpcTest::assertNetworkPeerPort))));

    if (emitOldRpcSemconv()) {
      testing()
          .waitAndAssertMetrics(
              "io.opentelemetry.sofa-rpc-5.4",
              "rpc.server.duration",
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
                                                  equalTo(
                                                      RPC_SERVICE,
                                                      "io.opentelemetry.instrumentation.sofarpc.v5_4.api.HelloService"),
                                                  equalTo(RPC_METHOD, "hello"))))));

      testing()
          .waitAndAssertMetrics(
              "io.opentelemetry.sofa-rpc-5.4",
              "rpc.client.duration",
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
                                                  equalTo(
                                                      RPC_SERVICE, GenericService.class.getName()),
                                                  equalTo(RPC_METHOD, "$invoke"),
                                                  equalTo(SERVER_ADDRESS, "127.0.0.1"),
                                                  satisfies(
                                                      SERVER_PORT,
                                                      val -> val.isInstanceOf(Long.class)),
                                                  satisfies(
                                                      NETWORK_TYPE,
                                                      AbstractSofaRpcTest::assertNetworkType))))));
    }

    if (emitStableRpcSemconv()) {
      testing()
          .waitAndAssertMetrics(
              "io.opentelemetry.sofa-rpc-5.4",
              "rpc.server.call.duration",
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
                                                  equalTo(
                                                      RPC_METHOD,
                                                      "io.opentelemetry.instrumentation.sofarpc.v5_4.api.HelloService/hello"))))));

      testing()
          .waitAndAssertMetrics(
              "io.opentelemetry.sofa-rpc-5.4",
              "rpc.client.call.duration",
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
                                                  equalTo(
                                                      RPC_METHOD,
                                                      "com.alipay.sofa.rpc.api.GenericService/$invoke"),
                                                  equalTo(SERVER_ADDRESS, "127.0.0.1"),
                                                  satisfies(
                                                      SERVER_PORT,
                                                      val -> val.isInstanceOf(Long.class)))))));
    }
  }

  @Test
  void testSofaRpcAsync() throws InterruptedException {
    int port = PortUtils.findOpenPort();

    ProviderConfig<HelloService> providerConfig = configureServer(port);
    cleanup.deferCleanup(providerConfig::unExport);
    providerConfig.export();

    ConsumerConfig<GenericService> consumerConfig = configureGenericClient(port);
    consumerConfig.setInvokeType(RpcConstants.INVOKER_TYPE_FUTURE);
    List<Filter> asyncFilters = new ArrayList<>(clientFilters());
    asyncFilters.add(new ClearRpcInternalContextFilter());
    consumerConfig.setFilterRef(asyncFilters);
    cleanup.deferCleanup(consumerConfig::unRefer);
    GenericService genericService = consumerConfig.refer();

    Object result =
        runWithSpan(
            "parent",
            () -> {
              genericService.$invoke(
                  "hello", new String[] {String.class.getName()}, new Object[] {"hello"});
              return SofaResponseFuture.getResponse(5000, false);
            });

    assertThat(result).isEqualTo("hello");

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                    span ->
                        span.hasName("com.alipay.sofa.rpc.api.GenericService/$invoke")
                            .hasKind(SpanKind.CLIENT)
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfyingExactly(
                                equalTo(RPC_SYSTEM, emitOldRpcSemconv() ? "sofarpc" : null),
                                equalTo(
                                    RPC_SYSTEM_NAME, emitStableRpcSemconv() ? "sofarpc" : null),
                                equalTo(
                                    RPC_SERVICE,
                                    emitOldRpcSemconv() ? GenericService.class.getName() : null),
                                equalTo(
                                    RPC_METHOD,
                                    emitStableRpcSemconv()
                                        ? "com.alipay.sofa.rpc.api.GenericService/$invoke"
                                        : "$invoke"),
                                equalTo(
                                    maybeStablePeerService(),
                                    hasPeerService() ? "test-peer-service" : null),
                                equalTo(SERVER_ADDRESS, "127.0.0.1"),
                                satisfies(SERVER_PORT, val -> val.isInstanceOf(Long.class)),
                                satisfies(
                                    NETWORK_PEER_ADDRESS,
                                    AbstractSofaRpcTest::assertNetworkPeerAddress),
                                satisfies(
                                    NETWORK_PEER_PORT, AbstractSofaRpcTest::assertNetworkPeerPort),
                                satisfies(NETWORK_TYPE, AbstractSofaRpcTest::assertNetworkType)),
                    span ->
                        span.hasName(
                                "io.opentelemetry.instrumentation.sofarpc.v5_4.api.HelloService/hello")
                            .hasKind(SpanKind.SERVER)
                            .hasParent(trace.getSpan(1))
                            .hasAttributesSatisfyingExactly(
                                equalTo(RPC_SYSTEM, emitOldRpcSemconv() ? "sofarpc" : null),
                                equalTo(
                                    RPC_SYSTEM_NAME, emitStableRpcSemconv() ? "sofarpc" : null),
                                equalTo(
                                    RPC_SERVICE,
                                    emitOldRpcSemconv()
                                        ? "io.opentelemetry.instrumentation.sofarpc.v5_4.api.HelloService"
                                        : null),
                                equalTo(
                                    RPC_METHOD,
                                    emitStableRpcSemconv()
                                        ? "io.opentelemetry.instrumentation.sofarpc.v5_4.api.HelloService/hello"
                                        : "hello"),
                                satisfies(
                                    NETWORK_PEER_ADDRESS,
                                    AbstractSofaRpcTest::assertNetworkPeerAddress),
                                satisfies(
                                    NETWORK_PEER_PORT, AbstractSofaRpcTest::assertNetworkPeerPort),
                                satisfies(NETWORK_TYPE, AbstractSofaRpcTest::assertNetworkType))));

    if (emitOldRpcSemconv()) {
      testing()
          .waitAndAssertMetrics(
              "io.opentelemetry.sofa-rpc-5.4",
              "rpc.server.duration",
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
                                                  equalTo(
                                                      RPC_SERVICE,
                                                      "io.opentelemetry.instrumentation.sofarpc.v5_4.api.HelloService"),
                                                  equalTo(RPC_METHOD, "hello"))))));

      testing()
          .waitAndAssertMetrics(
              "io.opentelemetry.sofa-rpc-5.4",
              "rpc.client.duration",
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
                                                  equalTo(
                                                      RPC_SERVICE, GenericService.class.getName()),
                                                  equalTo(RPC_METHOD, "$invoke"),
                                                  equalTo(SERVER_ADDRESS, "127.0.0.1"),
                                                  satisfies(
                                                      SERVER_PORT,
                                                      val -> val.isInstanceOf(Long.class)),
                                                  satisfies(
                                                      NETWORK_TYPE,
                                                      AbstractSofaRpcTest::assertNetworkType))))));
    }

    if (emitStableRpcSemconv()) {
      testing()
          .waitAndAssertMetrics(
              "io.opentelemetry.sofa-rpc-5.4",
              "rpc.server.call.duration",
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
                                                  equalTo(
                                                      RPC_METHOD,
                                                      "io.opentelemetry.instrumentation.sofarpc.v5_4.api.HelloService/hello"))))));

      testing()
          .waitAndAssertMetrics(
              "io.opentelemetry.sofa-rpc-5.4",
              "rpc.client.call.duration",
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
                                                  equalTo(
                                                      RPC_METHOD,
                                                      "com.alipay.sofa.rpc.api.GenericService/$invoke"),
                                                  equalTo(SERVER_ADDRESS, "127.0.0.1"),
                                                  satisfies(
                                                      SERVER_PORT,
                                                      val -> val.isInstanceOf(Long.class)))))));
    }
  }

  static void assertNetworkType(AbstractStringAssert<?> stringAssert) {
    stringAssert.satisfiesAnyOf(
        // this attribute is not filled reliably, it is either null or
        // "ipv4"/"ipv6"
        val -> assertThat(val).isNull(),
        val -> assertThat(val).isEqualTo("ipv4"),
        val -> assertThat(val).isEqualTo("ipv6"));
  }

  static void assertNetworkPeerAddress(AbstractAssert<?, ?> assertion) {
    assertion.satisfies(val -> assertThat(val).isEqualTo("127.0.0.1"));
  }

  static void assertNetworkPeerPort(AbstractAssert<?, ?> assertion) {
    assertion.satisfies(val -> assertThat((Long) val).isPositive());
  }

  private static class ClearRpcInternalContextFilter extends Filter {

    @Override
    @SuppressWarnings("ThrowsUncheckedException")
    public SofaResponse invoke(FilterInvoker invoker, SofaRequest request) throws SofaRpcException {
      return invoker.invoke(request);
    }

    @Override
    // Suppress rawtypes warning: SOFARPC Filter interface uses raw ConsumerConfig type
    @SuppressWarnings("rawtypes")
    public void onAsyncResponse(
        ConsumerConfig config, SofaRequest request, SofaResponse response, Throwable exception) {
      RpcInternalContext.removeContext();
    }
  }

  @Test
  void testSofaRpcRpcException() {
    int port = PortUtils.findOpenPort();

    ProviderConfig<ErrorService> providerConfig = configureErrorServer(port);
    cleanup.deferCleanup(providerConfig::unExport);
    providerConfig.export();

    ConsumerConfig<ErrorService> consumerConfig = configureErrorClient(port);
    cleanup.deferCleanup(consumerConfig::unRefer);
    ErrorService errorService = consumerConfig.refer();

    assertThatThrownBy(() -> runWithSpan("parent", errorService::throwException))
        .isInstanceOf(SofaRpcRuntimeException.class);

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                    span ->
                        span.hasName(
                                "io.opentelemetry.instrumentation.sofarpc.v5_4.api.ErrorService/throwException")
                            .hasKind(SpanKind.CLIENT)
                            .hasParent(trace.getSpan(0))
                            .hasStatus(StatusData.error())
                            .hasAttributesSatisfyingExactly(
                                equalTo(RPC_SYSTEM, emitOldRpcSemconv() ? "sofarpc" : null),
                                equalTo(
                                    RPC_SYSTEM_NAME, emitStableRpcSemconv() ? "sofarpc" : null),
                                equalTo(
                                    RPC_SERVICE,
                                    emitOldRpcSemconv()
                                        ? "io.opentelemetry.instrumentation.sofarpc.v5_4.api.ErrorService"
                                        : null),
                                equalTo(
                                    RPC_METHOD,
                                    emitStableRpcSemconv()
                                        ? "io.opentelemetry.instrumentation.sofarpc.v5_4.api.ErrorService/throwException"
                                        : "throwException"),
                                equalTo(
                                    maybeStablePeerService(),
                                    hasPeerService() ? "test-peer-service" : null),
                                equalTo(SERVER_ADDRESS, "127.0.0.1"),
                                satisfies(SERVER_PORT, val -> val.isInstanceOf(Long.class)),
                                satisfies(
                                    NETWORK_PEER_ADDRESS,
                                    AbstractSofaRpcTest::assertNetworkPeerAddress),
                                satisfies(
                                    NETWORK_PEER_PORT, AbstractSofaRpcTest::assertNetworkPeerPort),
                                satisfies(NETWORK_TYPE, AbstractSofaRpcTest::assertNetworkType),
                                equalTo(
                                    ERROR_TYPE,
                                    emitStableRpcSemconv()
                                        ? SofaRpcRuntimeException.class.getName()
                                        : null)),
                    span ->
                        span.hasName(
                                "io.opentelemetry.instrumentation.sofarpc.v5_4.api.ErrorService/throwException")
                            .hasKind(SpanKind.SERVER)
                            .hasParent(trace.getSpan(1))
                            .hasStatus(StatusData.error())
                            .hasAttributesSatisfyingExactly(
                                equalTo(RPC_SYSTEM, emitOldRpcSemconv() ? "sofarpc" : null),
                                equalTo(
                                    RPC_SYSTEM_NAME, emitStableRpcSemconv() ? "sofarpc" : null),
                                equalTo(
                                    RPC_SERVICE,
                                    emitOldRpcSemconv()
                                        ? "io.opentelemetry.instrumentation.sofarpc.v5_4.api.ErrorService"
                                        : null),
                                equalTo(
                                    RPC_METHOD,
                                    emitStableRpcSemconv()
                                        ? "io.opentelemetry.instrumentation.sofarpc.v5_4.api.ErrorService/throwException"
                                        : "throwException"),
                                satisfies(
                                    NETWORK_PEER_ADDRESS,
                                    AbstractSofaRpcTest::assertNetworkPeerAddress),
                                satisfies(
                                    NETWORK_PEER_PORT, AbstractSofaRpcTest::assertNetworkPeerPort),
                                equalTo(
                                    ERROR_TYPE,
                                    emitStableRpcSemconv()
                                        ? SofaRpcRuntimeException.class.getName()
                                        : null))));

    if (emitOldRpcSemconv()) {
      testing()
          .waitAndAssertMetrics(
              "io.opentelemetry.sofa-rpc-5.4",
              "rpc.server.duration",
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
                                                  equalTo(
                                                      RPC_SERVICE,
                                                      "io.opentelemetry.instrumentation.sofarpc.v5_4.api.ErrorService"),
                                                  equalTo(RPC_METHOD, "throwException"))))));

      testing()
          .waitAndAssertMetrics(
              "io.opentelemetry.sofa-rpc-5.4",
              "rpc.client.duration",
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
                                                  equalTo(
                                                      RPC_SERVICE,
                                                      "io.opentelemetry.instrumentation.sofarpc.v5_4.api.ErrorService"),
                                                  equalTo(RPC_METHOD, "throwException"),
                                                  equalTo(SERVER_ADDRESS, "127.0.0.1"),
                                                  satisfies(
                                                      SERVER_PORT,
                                                      val -> val.isInstanceOf(Long.class)),
                                                  satisfies(
                                                      NETWORK_TYPE,
                                                      AbstractSofaRpcTest::assertNetworkType))))));
    }

    if (emitStableRpcSemconv()) {
      testing()
          .waitAndAssertMetrics(
              "io.opentelemetry.sofa-rpc-5.4",
              "rpc.server.call.duration",
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
                                                  equalTo(
                                                      RPC_METHOD,
                                                      "io.opentelemetry.instrumentation.sofarpc.v5_4.api.ErrorService/throwException"))))));

      testing()
          .waitAndAssertMetrics(
              "io.opentelemetry.sofa-rpc-5.4",
              "rpc.client.call.duration",
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
                                                  equalTo(
                                                      RPC_METHOD,
                                                      "io.opentelemetry.instrumentation.sofarpc.v5_4.api.ErrorService/throwException"),
                                                  equalTo(SERVER_ADDRESS, "127.0.0.1"),
                                                  satisfies(
                                                      SERVER_PORT,
                                                      val -> val.isInstanceOf(Long.class)))))));
    }
  }

  @Test
  void testSofaRpcBusinessException() {
    int port = PortUtils.findOpenPort();

    // Start error service provider
    ProviderConfig<ErrorService> providerConfig = configureErrorServer(port);
    cleanup.deferCleanup(providerConfig::unExport);
    providerConfig.export();

    // Start consumer
    ConsumerConfig<ErrorService> consumerConfig = configureErrorClient(port);
    cleanup.deferCleanup(consumerConfig::unRefer);
    ErrorService errorService = consumerConfig.refer();

    assertThatThrownBy(() -> runWithSpan("parent", errorService::throwBusinessException))
        .isInstanceOf(IllegalStateException.class);

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                    span ->
                        span.hasName(
                                "io.opentelemetry.instrumentation.sofarpc.v5_4.api.ErrorService/throwBusinessException")
                            .hasKind(SpanKind.CLIENT)
                            .hasParent(trace.getSpan(0))
                            .hasStatus(StatusData.error())
                            .hasAttributesSatisfyingExactly(
                                equalTo(RPC_SYSTEM, emitOldRpcSemconv() ? "sofarpc" : null),
                                equalTo(
                                    RPC_SYSTEM_NAME, emitStableRpcSemconv() ? "sofarpc" : null),
                                equalTo(
                                    RPC_SERVICE,
                                    emitOldRpcSemconv()
                                        ? "io.opentelemetry.instrumentation.sofarpc.v5_4.api.ErrorService"
                                        : null),
                                equalTo(
                                    RPC_METHOD,
                                    emitStableRpcSemconv()
                                        ? "io.opentelemetry.instrumentation.sofarpc.v5_4.api.ErrorService/throwBusinessException"
                                        : "throwBusinessException"),
                                equalTo(
                                    maybeStablePeerService(),
                                    hasPeerService() ? "test-peer-service" : null),
                                equalTo(SERVER_ADDRESS, "127.0.0.1"),
                                satisfies(SERVER_PORT, val -> val.isInstanceOf(Long.class)),
                                satisfies(
                                    NETWORK_PEER_ADDRESS,
                                    AbstractSofaRpcTest::assertNetworkPeerAddress),
                                satisfies(
                                    NETWORK_PEER_PORT, AbstractSofaRpcTest::assertNetworkPeerPort),
                                satisfies(NETWORK_TYPE, AbstractSofaRpcTest::assertNetworkType),
                                equalTo(
                                    ERROR_TYPE,
                                    emitStableRpcSemconv()
                                        ? IllegalStateException.class.getName()
                                        : null)),
                    span ->
                        span.hasName(
                                "io.opentelemetry.instrumentation.sofarpc.v5_4.api.ErrorService/throwBusinessException")
                            .hasKind(SpanKind.SERVER)
                            .hasParent(trace.getSpan(1))
                            .hasStatus(StatusData.error())
                            .hasAttributesSatisfyingExactly(
                                equalTo(RPC_SYSTEM, emitOldRpcSemconv() ? "sofarpc" : null),
                                equalTo(
                                    RPC_SYSTEM_NAME, emitStableRpcSemconv() ? "sofarpc" : null),
                                equalTo(
                                    RPC_SERVICE,
                                    emitOldRpcSemconv()
                                        ? "io.opentelemetry.instrumentation.sofarpc.v5_4.api.ErrorService"
                                        : null),
                                equalTo(
                                    RPC_METHOD,
                                    emitStableRpcSemconv()
                                        ? "io.opentelemetry.instrumentation.sofarpc.v5_4.api.ErrorService/throwBusinessException"
                                        : "throwBusinessException"),
                                satisfies(
                                    NETWORK_PEER_ADDRESS,
                                    AbstractSofaRpcTest::assertNetworkPeerAddress),
                                satisfies(
                                    NETWORK_PEER_PORT, AbstractSofaRpcTest::assertNetworkPeerPort),
                                equalTo(
                                    ERROR_TYPE,
                                    emitStableRpcSemconv()
                                        ? IllegalStateException.class.getName()
                                        : null))));
  }

  @Test
  void testSofaRpcTimeout() {
    int port = PortUtils.findOpenPort();

    ProviderConfig<ErrorService> providerConfig = configureErrorServer(port);
    cleanup.deferCleanup(providerConfig::unExport);
    providerConfig.export();

    ConsumerConfig<ErrorService> consumerConfig = configureErrorClient(port);
    consumerConfig.setTimeout(1000); // 1 second timeout
    cleanup.deferCleanup(consumerConfig::unRefer);
    ErrorService errorService = consumerConfig.refer();

    assertThatThrownBy(() -> runWithSpan("parent", errorService::timeout))
        .isInstanceOf(SofaTimeOutException.class);
    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                    span ->
                        span.hasName(
                                "io.opentelemetry.instrumentation.sofarpc.v5_4.api.ErrorService/timeout")
                            .hasKind(SpanKind.CLIENT)
                            .hasParent(trace.getSpan(0))
                            .hasStatus(StatusData.error())
                            .hasAttributesSatisfyingExactly(
                                equalTo(RPC_SYSTEM, emitOldRpcSemconv() ? "sofarpc" : null),
                                equalTo(
                                    RPC_SYSTEM_NAME, emitStableRpcSemconv() ? "sofarpc" : null),
                                equalTo(
                                    RPC_SERVICE,
                                    emitOldRpcSemconv()
                                        ? "io.opentelemetry.instrumentation.sofarpc.v5_4.api.ErrorService"
                                        : null),
                                equalTo(
                                    RPC_METHOD,
                                    emitStableRpcSemconv()
                                        ? "io.opentelemetry.instrumentation.sofarpc.v5_4.api.ErrorService/timeout"
                                        : "timeout"),
                                equalTo(
                                    maybeStablePeerService(),
                                    hasPeerService() ? "test-peer-service" : null),
                                equalTo(SERVER_ADDRESS, "127.0.0.1"),
                                satisfies(SERVER_PORT, val -> val.isInstanceOf(Long.class)),
                                satisfies(
                                    NETWORK_PEER_ADDRESS,
                                    AbstractSofaRpcTest::assertNetworkPeerAddress),
                                satisfies(
                                    NETWORK_PEER_PORT, AbstractSofaRpcTest::assertNetworkPeerPort),
                                satisfies(NETWORK_TYPE, AbstractSofaRpcTest::assertNetworkType),
                                equalTo(
                                    ERROR_TYPE,
                                    emitStableRpcSemconv()
                                        ? SofaTimeOutException.class.getName()
                                        : null)),
                    // Server span: server completes normally (after 2s), so no error status
                    span ->
                        span.hasName(
                                "io.opentelemetry.instrumentation.sofarpc.v5_4.api.ErrorService/timeout")
                            .hasKind(SpanKind.SERVER)
                            .hasParent(trace.getSpan(1))
                            .hasAttributesSatisfyingExactly(
                                equalTo(RPC_SYSTEM, emitOldRpcSemconv() ? "sofarpc" : null),
                                equalTo(
                                    RPC_SYSTEM_NAME, emitStableRpcSemconv() ? "sofarpc" : null),
                                equalTo(
                                    RPC_SERVICE,
                                    emitOldRpcSemconv()
                                        ? "io.opentelemetry.instrumentation.sofarpc.v5_4.api.ErrorService"
                                        : null),
                                equalTo(
                                    RPC_METHOD,
                                    emitStableRpcSemconv()
                                        ? "io.opentelemetry.instrumentation.sofarpc.v5_4.api.ErrorService/timeout"
                                        : "timeout"),
                                satisfies(
                                    NETWORK_PEER_ADDRESS,
                                    AbstractSofaRpcTest::assertNetworkPeerAddress),
                                satisfies(
                                    NETWORK_PEER_PORT,
                                    AbstractSofaRpcTest::assertNetworkPeerPort))));
  }
}
