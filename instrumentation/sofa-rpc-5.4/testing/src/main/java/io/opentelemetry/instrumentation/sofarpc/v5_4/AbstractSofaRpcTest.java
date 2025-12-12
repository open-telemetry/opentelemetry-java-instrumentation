/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.sofarpc.v5_4;

import static io.opentelemetry.instrumentation.testing.GlobalTraceUtil.runWithSpan;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_TYPE;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.PeerIncubatingAttributes.PEER_SERVICE;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_METHOD;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SERVICE;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SYSTEM;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.alipay.sofa.rpc.api.GenericService;
import com.alipay.sofa.rpc.api.future.SofaResponseFuture;
import com.alipay.sofa.rpc.common.RpcConstants;
import com.alipay.sofa.rpc.config.ApplicationConfig;
import com.alipay.sofa.rpc.config.ConsumerConfig;
import com.alipay.sofa.rpc.config.ProviderConfig;
import com.alipay.sofa.rpc.config.ServerConfig;
import com.alipay.sofa.rpc.core.exception.SofaRpcRuntimeException;
import com.alipay.sofa.rpc.core.exception.SofaTimeOutException;
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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public abstract class AbstractSofaRpcTest {

  protected abstract InstrumentationExtension testing();

  protected abstract boolean hasPeerService();

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  @BeforeAll
  static void setUp() {}

  @AfterAll
  static void tearDown() {}

  ConsumerConfig<HelloService> configureClient(int port) {
    ConsumerConfig<HelloService> consumer = new ConsumerConfig<>();
    consumer
        .setInterfaceId(HelloService.class.getName())
        .setApplication(new ApplicationConfig().setAppName("sofa-rpc-test-consumer"))
        .setDirectUrl("bolt://127.0.0.1:" + port)
        .setRegister(false)
        .setTimeout(30000);
    return consumer;
  }

  ConsumerConfig<GenericService> configureGenericClient(int port) {
    ConsumerConfig<GenericService> consumer = new ConsumerConfig<>();
    consumer
        .setInterfaceId(HelloService.class.getName())
        .setApplication(new ApplicationConfig().setAppName("sofa-rpc-test-consumer"))
        .setDirectUrl("bolt://127.0.0.1:" + port)
        .setRegister(false)
        .setTimeout(30000)
        .setGeneric(true);
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
                                equalTo(RPC_SYSTEM, "sofa_rpc"),
                                equalTo(RPC_SERVICE, GenericService.class.getName()),
                                equalTo(RPC_METHOD, "$invoke"),
                                equalTo(
                                    PEER_SERVICE, hasPeerService() ? "test-peer-service" : null),
                                equalTo(SERVER_ADDRESS, "127.0.0.1"),
                                satisfies(SERVER_PORT, k -> k.isInstanceOf(Long.class)),
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
                            .hasAttributesSatisfying(
                                equalTo(RPC_SYSTEM, "sofa_rpc"),
                                equalTo(
                                    RPC_SERVICE,
                                    "io.opentelemetry.instrumentation.sofarpc.v5_4.api.HelloService"),
                                equalTo(RPC_METHOD, "hello"),
                                satisfies(
                                    NETWORK_PEER_ADDRESS,
                                    AbstractSofaRpcTest::assertNetworkPeerAddress),
                                satisfies(
                                    NETWORK_PEER_PORT,
                                    AbstractSofaRpcTest::assertNetworkPeerPort))));

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
                                                equalTo(RPC_SYSTEM, "sofa_rpc"),
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
                                                equalTo(RPC_SYSTEM, "sofa_rpc"),
                                                equalTo(
                                                    RPC_SERVICE, GenericService.class.getName()),
                                                equalTo(RPC_METHOD, "$invoke"),
                                                equalTo(SERVER_ADDRESS, "127.0.0.1"),
                                                satisfies(
                                                    SERVER_PORT, k -> k.isInstanceOf(Long.class)),
                                                satisfies(
                                                    NETWORK_TYPE,
                                                    AbstractSofaRpcTest::assertNetworkType))))));
  }

  @Test
  void testSofaRpcAsync() throws InterruptedException {
    int port = PortUtils.findOpenPort();

    ProviderConfig<HelloService> providerConfig = configureServer(port);
    cleanup.deferCleanup(providerConfig::unExport);
    providerConfig.export();

    ConsumerConfig<GenericService> consumerConfig = configureGenericClient(port);
    consumerConfig.setInvokeType(RpcConstants.INVOKER_TYPE_FUTURE);
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
                                equalTo(RPC_SYSTEM, "sofa_rpc"),
                                equalTo(RPC_SERVICE, GenericService.class.getName()),
                                equalTo(RPC_METHOD, "$invoke"),
                                equalTo(
                                    PEER_SERVICE, hasPeerService() ? "test-peer-service" : null),
                                equalTo(SERVER_ADDRESS, "127.0.0.1"),
                                satisfies(SERVER_PORT, k -> k.isInstanceOf(Long.class)),
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
                            .hasAttributesSatisfying(
                                equalTo(RPC_SYSTEM, "sofa_rpc"),
                                equalTo(
                                    RPC_SERVICE,
                                    "io.opentelemetry.instrumentation.sofarpc.v5_4.api.HelloService"),
                                equalTo(RPC_METHOD, "hello"),
                                satisfies(
                                    NETWORK_PEER_ADDRESS,
                                    AbstractSofaRpcTest::assertNetworkPeerAddress),
                                satisfies(
                                    NETWORK_PEER_PORT, AbstractSofaRpcTest::assertNetworkPeerPort),
                                satisfies(NETWORK_TYPE, AbstractSofaRpcTest::assertNetworkType))));

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
                                                equalTo(RPC_SYSTEM, "sofa_rpc"),
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
                                                equalTo(RPC_SYSTEM, "sofa_rpc"),
                                                equalTo(
                                                    RPC_SERVICE, GenericService.class.getName()),
                                                equalTo(RPC_METHOD, "$invoke"),
                                                equalTo(SERVER_ADDRESS, "127.0.0.1"),
                                                satisfies(
                                                    SERVER_PORT, k -> k.isInstanceOf(Long.class)),
                                                satisfies(
                                                    NETWORK_TYPE,
                                                    AbstractSofaRpcTest::assertNetworkType))))));
  }

  static void assertNetworkType(AbstractStringAssert<?> stringAssert) {
    stringAssert.satisfiesAnyOf(
        // this attribute is not filled reliably, it is either null or
        // "ipv4"/"ipv6"
        val -> assertThat(val).isNull(),
        val -> assertThat(val).isEqualTo("ipv4"),
        val -> assertThat(val).isEqualTo("ipv6"));
  }

  // Compatible with null returned by unresolved addresses
  static void assertNetworkPeerAddress(AbstractAssert<?, ?> assertion) {
    assertion.satisfiesAnyOf(
        val -> assertThat(val).isNull(), val -> assertThat(val).isInstanceOf(String.class));
  }

  // Compatible with null returned by unresolved addresses
  static void assertNetworkPeerPort(AbstractAssert<?, ?> assertion) {
    assertion.satisfiesAnyOf(
        val -> assertThat(val).isNull(), val -> assertThat(val).isInstanceOf(Long.class));
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
                                equalTo(RPC_SYSTEM, "sofa_rpc"),
                                equalTo(
                                    RPC_SERVICE,
                                    "io.opentelemetry.instrumentation.sofarpc.v5_4.api.ErrorService"),
                                equalTo(RPC_METHOD, "throwException"),
                                equalTo(
                                    PEER_SERVICE, hasPeerService() ? "test-peer-service" : null),
                                equalTo(SERVER_ADDRESS, "127.0.0.1"),
                                satisfies(SERVER_PORT, k -> k.isInstanceOf(Long.class)),
                                satisfies(
                                    NETWORK_PEER_ADDRESS,
                                    AbstractSofaRpcTest::assertNetworkPeerAddress),
                                satisfies(
                                    NETWORK_PEER_PORT, AbstractSofaRpcTest::assertNetworkPeerPort),
                                satisfies(NETWORK_TYPE, AbstractSofaRpcTest::assertNetworkType),
                                satisfies(
                                    ERROR_TYPE,
                                    errorType ->
                                        errorType
                                            .isInstanceOf(String.class)
                                            .satisfies(
                                                str ->
                                                    assertThat(str)
                                                        .contains("SofaRpcRuntimeException")))),
                    span ->
                        span.hasName(
                                "io.opentelemetry.instrumentation.sofarpc.v5_4.api.ErrorService/throwException")
                            .hasKind(SpanKind.SERVER)
                            .hasParent(trace.getSpan(1))
                            .hasStatus(StatusData.error())
                            .hasAttributesSatisfying(
                                equalTo(RPC_SYSTEM, "sofa_rpc"),
                                equalTo(
                                    RPC_SERVICE,
                                    "io.opentelemetry.instrumentation.sofarpc.v5_4.api.ErrorService"),
                                equalTo(RPC_METHOD, "throwException"),
                                satisfies(
                                    NETWORK_PEER_ADDRESS,
                                    AbstractSofaRpcTest::assertNetworkPeerAddress),
                                satisfies(
                                    NETWORK_PEER_PORT, AbstractSofaRpcTest::assertNetworkPeerPort),
                                satisfies(
                                    ERROR_TYPE,
                                    errorType ->
                                        errorType
                                            .isInstanceOf(String.class)
                                            .satisfies(
                                                str ->
                                                    assertThat(str)
                                                        .contains("SofaRpcRuntimeException"))))));
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
                                                equalTo(RPC_SYSTEM, "sofa_rpc"),
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
                                            point.hasAttributesSatisfying(
                                                equalTo(RPC_SYSTEM, "sofa_rpc"),
                                                equalTo(
                                                    RPC_SERVICE,
                                                    "io.opentelemetry.instrumentation.sofarpc.v5_4.api.ErrorService"),
                                                equalTo(RPC_METHOD, "throwException"),
                                                equalTo(SERVER_ADDRESS, "127.0.0.1"),
                                                satisfies(
                                                    SERVER_PORT, k -> k.isInstanceOf(Long.class)),
                                                satisfies(
                                                    NETWORK_TYPE,
                                                    AbstractSofaRpcTest::assertNetworkType))))));
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
                                equalTo(RPC_SYSTEM, "sofa_rpc"),
                                equalTo(
                                    RPC_SERVICE,
                                    "io.opentelemetry.instrumentation.sofarpc.v5_4.api.ErrorService"),
                                equalTo(RPC_METHOD, "throwBusinessException"),
                                equalTo(
                                    PEER_SERVICE, hasPeerService() ? "test-peer-service" : null),
                                equalTo(SERVER_ADDRESS, "127.0.0.1"),
                                satisfies(SERVER_PORT, k -> k.isInstanceOf(Long.class)),
                                satisfies(
                                    NETWORK_PEER_ADDRESS,
                                    AbstractSofaRpcTest::assertNetworkPeerAddress),
                                satisfies(
                                    NETWORK_PEER_PORT, AbstractSofaRpcTest::assertNetworkPeerPort),
                                satisfies(NETWORK_TYPE, AbstractSofaRpcTest::assertNetworkType),
                                satisfies(
                                    ERROR_TYPE,
                                    errorType ->
                                        errorType
                                            .isInstanceOf(String.class)
                                            .satisfies(
                                                str ->
                                                    assertThat(str)
                                                        .contains("IllegalStateException")))),
                    span ->
                        span.hasName(
                                "io.opentelemetry.instrumentation.sofarpc.v5_4.api.ErrorService/throwBusinessException")
                            .hasKind(SpanKind.SERVER)
                            .hasParent(trace.getSpan(1))
                            .hasStatus(StatusData.error())
                            .hasAttributesSatisfying(
                                equalTo(RPC_SYSTEM, "sofa_rpc"),
                                equalTo(
                                    RPC_SERVICE,
                                    "io.opentelemetry.instrumentation.sofarpc.v5_4.api.ErrorService"),
                                equalTo(RPC_METHOD, "throwBusinessException"),
                                satisfies(
                                    NETWORK_PEER_ADDRESS,
                                    AbstractSofaRpcTest::assertNetworkPeerAddress),
                                satisfies(
                                    NETWORK_PEER_PORT, AbstractSofaRpcTest::assertNetworkPeerPort),
                                satisfies(
                                    ERROR_TYPE,
                                    errorType ->
                                        errorType
                                            .isInstanceOf(String.class)
                                            .satisfies(
                                                str ->
                                                    assertThat(str)
                                                        .contains("IllegalStateException"))))));
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
                                equalTo(RPC_SYSTEM, "sofa_rpc"),
                                equalTo(
                                    RPC_SERVICE,
                                    "io.opentelemetry.instrumentation.sofarpc.v5_4.api.ErrorService"),
                                equalTo(RPC_METHOD, "timeout"),
                                equalTo(
                                    PEER_SERVICE, hasPeerService() ? "test-peer-service" : null),
                                equalTo(SERVER_ADDRESS, "127.0.0.1"),
                                satisfies(SERVER_PORT, k -> k.isInstanceOf(Long.class)),
                                satisfies(
                                    NETWORK_PEER_ADDRESS,
                                    AbstractSofaRpcTest::assertNetworkPeerAddress),
                                satisfies(
                                    NETWORK_PEER_PORT, AbstractSofaRpcTest::assertNetworkPeerPort),
                                satisfies(NETWORK_TYPE, AbstractSofaRpcTest::assertNetworkType),
                                satisfies(
                                    ERROR_TYPE,
                                    errorType ->
                                        errorType
                                            .isInstanceOf(String.class)
                                            .satisfies(
                                                str ->
                                                    assertThat(str)
                                                        .satisfiesAnyOf(
                                                            s -> assertThat(s).contains("timeout"),
                                                            s -> assertThat(s).contains("Timeout"),
                                                            s ->
                                                                assertThat(s)
                                                                    .contains(
                                                                        "SofaTimeOutException")))))));
  }
}
