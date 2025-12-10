/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.sofa.rpc;

import static io.opentelemetry.instrumentation.testing.GlobalTraceUtil.runWithSpan;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_TYPE;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.PeerIncubatingAttributes.PEER_SERVICE;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_METHOD;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SERVICE;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SYSTEM;

import com.alipay.sofa.rpc.api.GenericService;
import com.alipay.sofa.rpc.config.ApplicationConfig;
import com.alipay.sofa.rpc.config.ConsumerConfig;
import com.alipay.sofa.rpc.config.ProviderConfig;
import com.alipay.sofa.rpc.config.ServerConfig;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.sofa.rpc.api.HelloService;
import io.opentelemetry.instrumentation.sofa.rpc.api.MiddleService;
import io.opentelemetry.instrumentation.sofa.rpc.impl.HelloServiceImpl;
import io.opentelemetry.instrumentation.sofa.rpc.impl.MiddleServiceImpl;
import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public abstract class AbstractSofaRpcTraceChainTest {

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  @BeforeAll
  static void setUp() {
    // SOFARPC doesn't need special setup like Dubbo
  }

  @AfterAll
  static void tearDown() {
    // Cleanup is handled by AutoCleanupExtension
  }

  protected abstract InstrumentationExtension testing();

  protected abstract boolean hasPeerService();

  ConsumerConfig<HelloService> configureClient(int port) {
    ConsumerConfig<HelloService> consumer = new ConsumerConfig<>();
    consumer.setInterfaceId(HelloService.class.getName())
        .setApplication(new ApplicationConfig().setAppName("sofa-rpc-test-consumer"))
        .setDirectUrl("bolt://127.0.0.1:" + port)
        .setRegister(false)
        .setTimeout(30000);
    return consumer;
  }

  ConsumerConfig<HelloService> configureLocalClient(int port) {
    ConsumerConfig<HelloService> consumer = new ConsumerConfig<>();
    consumer.setInterfaceId(HelloService.class.getName())
        .setApplication(new ApplicationConfig().setAppName("sofa-rpc-test-consumer"))
        .setDirectUrl("local://127.0.0.1:" + port)
        .setRegister(false)
        .setInJVM(true)
        .setTimeout(30000);
    return consumer;
  }

  ConsumerConfig<GenericService> configureGenericMiddleClient(int port) {
    ConsumerConfig<GenericService> consumer = new ConsumerConfig<>();
    consumer.setInterfaceId(MiddleService.class.getName())
        .setApplication(new ApplicationConfig().setAppName("sofa-rpc-test-consumer"))
        .setDirectUrl("bolt://127.0.0.1:" + port)
        .setRegister(false)
        .setTimeout(30000)
        .setGeneric(true);
    return consumer;
  }


  ServerConfig getServerConfig(int port) {
    return new ServerConfig()
        .setProtocol("bolt")
        .setHost("127.0.0.1")
        .setPort(port)
        .setDaemon(false);
  }

  ProviderConfig<HelloService> configureServer(int port) {
    ProviderConfig<HelloService> provider = new ProviderConfig<>();
    provider.setInterfaceId(HelloService.class.getName())
        .setRef(new HelloServiceImpl())
        .setApplication(new ApplicationConfig().setAppName("sofa-rpc-test-provider"))
        .setServer(getServerConfig(port))
        .setRegister(false);
    return provider;
  }

  ProviderConfig<MiddleService> configureMiddleServer(int port, ConsumerConfig<HelloService> consumerConfig) {
    ProviderConfig<MiddleService> provider = new ProviderConfig<>();
    provider.setInterfaceId(MiddleService.class.getName())
        .setRef(new MiddleServiceImpl(consumerConfig))
        .setApplication(new ApplicationConfig().setAppName("sofa-rpc-test-middle"))
        .setServer(getServerConfig(port))
        .setRegister(false);
    return provider;
  }

  @Test
  @DisplayName("test that context is propagated correctly in chained sofa-rpc calls")
  void testSofaRpcChain() {
    int port = PortUtils.findOpenPorts(2);
    int middlePort = port + 1;

    // Setup hello service provider
    ProviderConfig<HelloService> helloProviderConfig = configureServer(port);
    cleanup.deferCleanup(helloProviderConfig::unExport);
    helloProviderConfig.export();

    // Setup middle service provider, hello service consumer
    ConsumerConfig<HelloService> helloConsumerConfig = configureClient(port);
    cleanup.deferCleanup(helloConsumerConfig::unRefer);
    
    ProviderConfig<MiddleService> middleProviderConfig = configureMiddleServer(middlePort, helloConsumerConfig);
    cleanup.deferCleanup(middleProviderConfig::unExport);
    middleProviderConfig.export();

    // Setup middle service consumer
    ConsumerConfig<GenericService> middleConsumerConfig = configureGenericMiddleClient(middlePort);
    cleanup.deferCleanup(middleConsumerConfig::unRefer);
    GenericService genericMiddleService = middleConsumerConfig.refer();

    Object response = runWithSpan("parent",
        () ->
            genericMiddleService.$invoke(
                "hello", new String[] {String.class.getName()}, new Object[] {"hello"}));

    assertThat(response).isEqualTo("hello");
    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                    span ->
                        span.hasName(
                                "com.alipay.sofa.rpc.api.GenericService/$invoke")
                            .hasKind(SpanKind.CLIENT)
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfyingExactly(
                                equalTo(RPC_SYSTEM, "sofa_rpc"),
                                equalTo(RPC_SERVICE, GenericService.class.getName()),
                                equalTo(RPC_METHOD, "$invoke"),
                                equalTo(PEER_SERVICE, hasPeerService() ? "test-peer-service" : null),
                                equalTo(SERVER_ADDRESS, "127.0.0.1"),
                                satisfies(SERVER_PORT, k -> k.isInstanceOf(Long.class)),
                                satisfies(
                                    NETWORK_PEER_ADDRESS,
                                    k -> k.isInstanceOf(String.class)),
                                satisfies(
                                    NETWORK_PEER_PORT,
                                    k -> k.isInstanceOf(Long.class)),
                                satisfies(NETWORK_TYPE, AbstractSofaRpcTest::assertNetworkType)),
                    span ->
                        span.hasName(
                                "io.opentelemetry.instrumentation.sofa.rpc.api.MiddleService/hello")
                            .hasKind(SpanKind.SERVER)
                            .hasParent(trace.getSpan(1))
                            .hasAttributesSatisfying(
                                equalTo(RPC_SYSTEM, "sofa_rpc"),
                                equalTo(
                                    RPC_SERVICE,
                                    "io.opentelemetry.instrumentation.sofa.rpc.api.MiddleService"),
                                equalTo(RPC_METHOD, "hello"),
                                satisfies(NETWORK_PEER_ADDRESS, k -> k.isInstanceOf(String.class)),
                                satisfies(NETWORK_PEER_PORT, k -> k.isInstanceOf(Long.class))),
                    span ->
                        span.hasName(
                                "io.opentelemetry.instrumentation.sofa.rpc.api.HelloService/hello")
                            .hasKind(SpanKind.CLIENT)
                            .hasParent(trace.getSpan(2))
                            .hasAttributesSatisfyingExactly(
                                equalTo(RPC_SYSTEM, "sofa_rpc"),
                                equalTo(
                                    RPC_SERVICE,
                                    "io.opentelemetry.instrumentation.sofa.rpc.api.HelloService"),
                                equalTo(RPC_METHOD, "hello"),
                                equalTo(PEER_SERVICE, hasPeerService() ? "test-peer-service" : null),
                                equalTo(SERVER_ADDRESS, "127.0.0.1"),
                                satisfies(SERVER_PORT, k -> k.isInstanceOf(Long.class)),
                                satisfies(
                                    NETWORK_PEER_ADDRESS,
                                    k -> k.isInstanceOf(String.class)),
                                satisfies(
                                    NETWORK_PEER_PORT,
                                    k -> k.isInstanceOf(Long.class)),
                                satisfies(NETWORK_TYPE, AbstractSofaRpcTest::assertNetworkType)),
                    span ->
                        span.hasName(
                                "io.opentelemetry.instrumentation.sofa.rpc.api.HelloService/hello")
                            .hasKind(SpanKind.SERVER)
                            .hasParent(trace.getSpan(3))
                            .hasAttributesSatisfying(
                                equalTo(RPC_SYSTEM, "sofa_rpc"),
                                equalTo(
                                    RPC_SERVICE,
                                    "io.opentelemetry.instrumentation.sofa.rpc.api.HelloService"),
                                equalTo(RPC_METHOD, "hello"),
                                satisfies(NETWORK_PEER_ADDRESS, k -> k.isInstanceOf(String.class)),
                                satisfies(NETWORK_PEER_PORT, k -> k.isInstanceOf(Long.class)))));

    testing()
        .waitAndAssertMetrics(
            "io.opentelemetry.sofa-rpc",
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
                                                    "io.opentelemetry.instrumentation.sofa.rpc.api.HelloService"),
                                                equalTo(RPC_METHOD, "hello")),
                                        point ->
                                            point.hasAttributesSatisfyingExactly(
                                                equalTo(RPC_SYSTEM, "sofa_rpc"),
                                                equalTo(
                                                    RPC_SERVICE,
                                                    "io.opentelemetry.instrumentation.sofa.rpc.api.MiddleService"),
                                                equalTo(RPC_METHOD, "hello"))))));

    testing()
        .waitAndAssertMetrics(
            "io.opentelemetry.sofa-rpc",
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
                                                equalTo(RPC_SERVICE, GenericService.class.getName()),
                                                equalTo(RPC_METHOD, "$invoke"),
                                                equalTo(SERVER_ADDRESS, "127.0.0.1"),
                                                satisfies(
                                                    SERVER_PORT, k -> k.isInstanceOf(Long.class)),
                                                satisfies(
                                                    NETWORK_TYPE,
                                                    AbstractSofaRpcTest::assertNetworkType)),
                                        point ->
                                            point.hasAttributesSatisfyingExactly(
                                                equalTo(RPC_SYSTEM, "sofa_rpc"),
                                                equalTo(
                                                    RPC_SERVICE,
                                                    "io.opentelemetry.instrumentation.sofa.rpc.api.HelloService"),
                                                equalTo(RPC_METHOD, "hello"),
                                                equalTo(SERVER_ADDRESS, "127.0.0.1"),
                                                satisfies(
                                                    SERVER_PORT, k -> k.isInstanceOf(Long.class)),
                                                satisfies(
                                                    NETWORK_TYPE,
                                                    AbstractSofaRpcTest::assertNetworkType))))));
  }

  @Test
  @DisplayName("test ignore local calls")
  void testSofaRpcChainLocal() {
    int port = PortUtils.findOpenPort();

    // Setup middle service provider with HelloService provider in same process for local calls
    ConsumerConfig<HelloService> helloConsumerConfig = configureLocalClient(port);
    cleanup.deferCleanup(helloConsumerConfig::unRefer);

    ProviderConfig<HelloService> helloProviderConfig = configureServer(port);
    cleanup.deferCleanup(helloProviderConfig::unExport);
    helloProviderConfig.export();

    ProviderConfig<MiddleService> middleProviderConfig = configureMiddleServer(port, helloConsumerConfig);
    cleanup.deferCleanup(middleProviderConfig::unExport);
    middleProviderConfig.export();

    // Setup middle service consumer
    ConsumerConfig<GenericService> middleConsumerConfig = configureGenericMiddleClient(port);
    cleanup.deferCleanup(middleConsumerConfig::unRefer);
    GenericService genericMiddleService = middleConsumerConfig.refer();

    Object response = runWithSpan("parent",
        () ->
            genericMiddleService.$invoke(
                "hello", new String[] {String.class.getName()}, new Object[] {"hello"}));

    assertThat(response).isEqualTo("hello");
    // Strategy: Only skip CLIENT spans for local calls, keep SERVER spans
    // The local call from MiddleService to HelloService will:
    // - Skip CLIENT span (detected via ConsumerConfig.isInJVM() or directUrl="local://")
    // - Keep SERVER span (server cannot reliably detect local calls)
    // Note: Since the CLIENT span is skipped, trace context may not propagate correctly,
    // resulting in HelloService SERVER span appearing in a separate trace
    testing()
        .waitAndAssertTraces(
            // First trace: parent -> MiddleService CLIENT -> MiddleService SERVER
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                    span ->
                        span.hasName(
                                "com.alipay.sofa.rpc.api.GenericService/$invoke")
                            .hasKind(SpanKind.CLIENT)
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfyingExactly(
                                equalTo(RPC_SYSTEM, "sofa_rpc"),
                                equalTo(RPC_SERVICE, GenericService.class.getName()),
                                equalTo(RPC_METHOD, "$invoke"),
                                equalTo(PEER_SERVICE, hasPeerService() ? "test-peer-service" : null),
                                equalTo(SERVER_ADDRESS, "127.0.0.1"),
                                satisfies(SERVER_PORT, k -> k.isInstanceOf(Long.class)),
                                satisfies(
                                    NETWORK_PEER_ADDRESS,
                                    k -> k.isInstanceOf(String.class)),
                                satisfies(
                                    NETWORK_PEER_PORT,
                                    k -> k.isInstanceOf(Long.class)),
                                satisfies(NETWORK_TYPE, AbstractSofaRpcTest::assertNetworkType)),
                    span ->
                        span.hasName(
                                "io.opentelemetry.instrumentation.sofa.rpc.api.MiddleService/hello")
                            .hasKind(SpanKind.SERVER)
                            .hasParent(trace.getSpan(1))
                            .hasAttributesSatisfying(
                                equalTo(RPC_SYSTEM, "sofa_rpc"),
                                equalTo(
                                    RPC_SERVICE,
                                    "io.opentelemetry.instrumentation.sofa.rpc.api.MiddleService"),
                                equalTo(RPC_METHOD, "hello"),
                                satisfies(NETWORK_PEER_ADDRESS, k -> k.isInstanceOf(String.class)),
                                satisfies(NETWORK_PEER_PORT, k -> k.isInstanceOf(Long.class)))),
            // Second trace: HelloService SERVER span (appears in separate trace because CLIENT span was skipped)
            // This is expected behavior when CLIENT span is skipped - trace context may not propagate
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName(
                                "io.opentelemetry.instrumentation.sofa.rpc.api.HelloService/hello")
                            .hasKind(SpanKind.SERVER)
                            .hasNoParent() // No parent because CLIENT span was skipped
                            .hasAttributesSatisfying(
                                equalTo(RPC_SYSTEM, "sofa_rpc"),
                                equalTo(
                                    RPC_SERVICE,
                                    "io.opentelemetry.instrumentation.sofa.rpc.api.HelloService"),
                                equalTo(RPC_METHOD, "hello"),
                                satisfies(NETWORK_PEER_ADDRESS, k -> k.isInstanceOf(String.class)),
                                satisfies(NETWORK_PEER_PORT, k -> k.isInstanceOf(Long.class)))));

    testing()
        .waitAndAssertMetrics(
            "io.opentelemetry.sofa-rpc",
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
                                                    "io.opentelemetry.instrumentation.sofa.rpc.api.MiddleService"),
                                                equalTo(RPC_METHOD, "hello")),
                                        // HelloService SERVER metrics are also kept for local calls
                                        point ->
                                            point.hasAttributesSatisfyingExactly(
                                                equalTo(RPC_SYSTEM, "sofa_rpc"),
                                                equalTo(
                                                    RPC_SERVICE,
                                                    "io.opentelemetry.instrumentation.sofa.rpc.api.HelloService"),
                                                equalTo(RPC_METHOD, "hello"))))));

    testing()
        .waitAndAssertMetrics(
            "io.opentelemetry.sofa-rpc",
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
                                                equalTo(RPC_SERVICE, GenericService.class.getName()),
                                                equalTo(RPC_METHOD, "$invoke"),
                                                equalTo(SERVER_ADDRESS, "127.0.0.1"),
                                                satisfies(
                                                    SERVER_PORT, k -> k.isInstanceOf(Long.class)),
                                                satisfies(
                                                    NETWORK_TYPE,
                                                    AbstractSofaRpcTest::assertNetworkType))))));
  }
}

