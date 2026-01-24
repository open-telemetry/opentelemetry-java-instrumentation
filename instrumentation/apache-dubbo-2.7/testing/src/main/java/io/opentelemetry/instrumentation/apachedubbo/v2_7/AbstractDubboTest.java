/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v2_7;

import static io.opentelemetry.instrumentation.testing.GlobalTraceUtil.runWithSpan;
import static io.opentelemetry.instrumentation.testing.junit.rpc.RpcSemconvStabilityUtil.getClientDurationMetricName;
import static io.opentelemetry.instrumentation.testing.junit.rpc.RpcSemconvStabilityUtil.getDurationUnit;
import static io.opentelemetry.instrumentation.testing.junit.rpc.RpcSemconvStabilityUtil.getServerDurationMetricName;
import static io.opentelemetry.instrumentation.testing.junit.rpc.RpcSemconvStabilityUtil.rpcMethodAssertions;
import static io.opentelemetry.instrumentation.testing.junit.rpc.RpcSemconvStabilityUtil.rpcSystemAssertion;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_TYPE;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.PeerIncubatingAttributes.PEER_SERVICE;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.apachedubbo.v2_7.api.HelloService;
import io.opentelemetry.instrumentation.apachedubbo.v2_7.impl.HelloServiceImpl;
import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import org.apache.dubbo.common.utils.NetUtils;
import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ProtocolConfig;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.RegistryConfig;
import org.apache.dubbo.config.ServiceConfig;
import org.apache.dubbo.config.bootstrap.DubboBootstrap;
import org.apache.dubbo.rpc.service.GenericService;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.AbstractStringAssert;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public abstract class AbstractDubboTest {

  private final ProtocolConfig protocolConfig = new ProtocolConfig();

  protected abstract InstrumentationExtension testing();

  protected abstract boolean hasPeerService();

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  @BeforeAll
  static void setUp() throws Exception {
    System.setProperty("dubbo.application.qos-enable", "false");
    Field field = NetUtils.class.getDeclaredField("LOCAL_ADDRESS");
    field.setAccessible(true);
    field.set(null, InetAddress.getLoopbackAddress());
  }

  @AfterAll
  static void tearDown() {
    System.clearProperty("dubbo.application.qos-enable");
  }

  ReferenceConfig<HelloService> configureClient(int port) {
    ReferenceConfig<HelloService> reference = new ReferenceConfig<>();
    reference.setInterface(HelloService.class);
    reference.setGeneric("true");
    reference.setUrl("dubbo://localhost:" + port + "/?timeout=30000");
    return reference;
  }

  ServiceConfig<HelloServiceImpl> configureServer() {
    RegistryConfig registerConfig = new RegistryConfig();
    registerConfig.setAddress("N/A");
    ServiceConfig<HelloServiceImpl> service = new ServiceConfig<>();
    service.setInterface(HelloService.class);
    service.setRef(new HelloServiceImpl());
    service.setRegistry(registerConfig);
    return service;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  ReferenceConfig<GenericService> convertReference(ReferenceConfig<HelloService> config) {
    return (ReferenceConfig) config;
  }

  private List<AttributeAssertion> buildClientSpanAttributes(String service, String method) {
    List<AttributeAssertion> attrs = new ArrayList<>();
    attrs.add(rpcSystemAssertion("apache_dubbo"));
    attrs.addAll(rpcMethodAssertions(service, method));
    if (hasPeerService()) {
      attrs.add(equalTo(PEER_SERVICE, "test-peer-service"));
    }
    attrs.add(equalTo(SERVER_ADDRESS, "localhost"));
    attrs.add(satisfies(SERVER_PORT, k -> k.isInstanceOf(Long.class)));
    attrs.add(
        satisfies(
            NETWORK_PEER_ADDRESS, k -> assertLatestDeps(k, a -> a.isInstanceOf(String.class))));
    attrs.add(
        satisfies(NETWORK_PEER_PORT, k -> assertLatestDeps(k, a -> a.isInstanceOf(Long.class))));
    attrs.add(satisfies(NETWORK_TYPE, AbstractDubboTest::assertNetworkType));
    return attrs;
  }

  private static List<AttributeAssertion> buildServerSpanAttributes(String service, String method) {
    List<AttributeAssertion> attrs = new ArrayList<>();
    attrs.add(rpcSystemAssertion("apache_dubbo"));
    attrs.addAll(rpcMethodAssertions(service, method));
    attrs.add(satisfies(NETWORK_PEER_ADDRESS, k -> k.isInstanceOf(String.class)));
    attrs.add(satisfies(NETWORK_PEER_PORT, k -> k.isInstanceOf(Long.class)));
    return attrs;
  }

  private static List<AttributeAssertion> buildMetricAttributes(String service, String method) {
    List<AttributeAssertion> attrs = new ArrayList<>();
    attrs.add(rpcSystemAssertion("apache_dubbo"));
    attrs.addAll(rpcMethodAssertions(service, method));
    return attrs;
  }

  private static List<AttributeAssertion> buildClientMetricAttributes(
      String service, String method) {
    List<AttributeAssertion> attrs = buildMetricAttributes(service, method);
    attrs.add(equalTo(SERVER_ADDRESS, "localhost"));
    attrs.add(satisfies(SERVER_PORT, k -> k.isInstanceOf(Long.class)));
    if (SemconvStability.emitOldRpcSemconv()) {
      attrs.add(satisfies(NETWORK_TYPE, AbstractDubboTest::assertNetworkType));
    }
    return attrs;
  }

  @Test
  void testApacheDubboBase() throws ReflectiveOperationException {
    int port = PortUtils.findOpenPort();
    protocolConfig.setPort(port);
    // provider boostrap
    DubboBootstrap bootstrap = DubboTestUtil.newDubboBootstrap();
    cleanup.deferCleanup(bootstrap::destroy);
    bootstrap
        .application(new ApplicationConfig("dubbo-test-provider"))
        .service(configureServer())
        .protocol(protocolConfig)
        .start();

    // consumer boostrap
    DubboBootstrap consumerBootstrap = DubboTestUtil.newDubboBootstrap();
    cleanup.deferCleanup(consumerBootstrap::destroy);
    ReferenceConfig<HelloService> referenceConfig = configureClient(port);
    ProtocolConfig consumerProtocolConfig = new ProtocolConfig();
    consumerProtocolConfig.setRegister(false);
    consumerBootstrap
        .application(new ApplicationConfig("dubbo-demo-api-consumer"))
        .reference(referenceConfig)
        .protocol(consumerProtocolConfig)
        .start();

    // generic call
    ReferenceConfig<GenericService> reference = convertReference(referenceConfig);
    GenericService genericService = reference.get();

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
                        span.hasName("org.apache.dubbo.rpc.service.GenericService/$invoke")
                            .hasKind(SpanKind.CLIENT)
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfying(
                                buildClientSpanAttributes(
                                    "org.apache.dubbo.rpc.service.GenericService", "$invoke")),
                    span ->
                        span.hasName(
                                "io.opentelemetry.instrumentation.apachedubbo.v2_7.api.HelloService/hello")
                            .hasKind(SpanKind.SERVER)
                            .hasParent(trace.getSpan(1))
                            .hasAttributesSatisfying(
                                buildServerSpanAttributes(
                                    "io.opentelemetry.instrumentation.apachedubbo.v2_7.api.HelloService",
                                    "hello"))));

    testing()
        .waitAndAssertMetrics(
            "io.opentelemetry.apache-dubbo-2.7",
            getServerDurationMetricName(),
            metrics ->
                metrics.anySatisfy(
                    metric ->
                        assertThat(metric)
                            .hasUnit(getDurationUnit())
                            .hasHistogramSatisfying(
                                histogram ->
                                    histogram.hasPointsSatisfying(
                                        point ->
                                            point.hasAttributesSatisfyingExactly(
                                                buildMetricAttributes(
                                                    "io.opentelemetry.instrumentation.apachedubbo.v2_7.api.HelloService",
                                                    "hello"))))));

    testing()
        .waitAndAssertMetrics(
            "io.opentelemetry.apache-dubbo-2.7",
            getClientDurationMetricName(),
            metrics ->
                metrics.anySatisfy(
                    metric ->
                        assertThat(metric)
                            .hasUnit(getDurationUnit())
                            .hasHistogramSatisfying(
                                histogram ->
                                    histogram.hasPointsSatisfying(
                                        point ->
                                            point.hasAttributesSatisfyingExactly(
                                                buildClientMetricAttributes(
                                                    "org.apache.dubbo.rpc.service.GenericService",
                                                    "$invoke"))))));
  }

  @Test
  void testApacheDubboTest()
      throws ExecutionException, InterruptedException, ReflectiveOperationException {
    int port = PortUtils.findOpenPort();
    protocolConfig.setPort(port);

    DubboBootstrap bootstrap = DubboTestUtil.newDubboBootstrap();
    cleanup.deferCleanup(bootstrap::destroy);
    bootstrap
        .application(new ApplicationConfig("dubbo-test-async-provider"))
        .service(configureServer())
        .protocol(protocolConfig)
        .start();

    ProtocolConfig consumerProtocolConfig = new ProtocolConfig();
    consumerProtocolConfig.setRegister(false);

    ReferenceConfig<HelloService> referenceConfig = configureClient(port);
    DubboBootstrap consumerBootstrap = DubboTestUtil.newDubboBootstrap();
    cleanup.deferCleanup(consumerBootstrap::destroy);
    consumerBootstrap
        .application(new ApplicationConfig("dubbo-demo-async-api-consumer"))
        .reference(referenceConfig)
        .protocol(consumerProtocolConfig)
        .start();

    // generic call
    ReferenceConfig<GenericService> reference = convertReference(referenceConfig);
    GenericService genericService = reference.get();
    CompletableFuture<Object> response =
        runWithSpan(
            "parent",
            () ->
                genericService.$invokeAsync(
                    "hello", new String[] {String.class.getName()}, new Object[] {"hello"}));

    assertThat(response.get()).isEqualTo("hello");

    // Build server span attributes with special network.type handling for this test
    List<AttributeAssertion> attributes =
        buildServerSpanAttributes(
            "io.opentelemetry.instrumentation.apachedubbo.v2_7.api.HelloService", "hello");
    // this attribute is not filled reliably, it is either null or "ipv4"/"ipv6"
    attributes.add(
        satisfies(
            NETWORK_TYPE,
            k ->
                assertLatestDeps(
                    k,
                    a ->
                        a.satisfiesAnyOf(
                            val -> assertThat(val).isNull(),
                            val -> assertThat(val).isEqualTo("ipv4"),
                            val -> assertThat(val).isEqualTo("ipv6")))));

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                    span ->
                        span.hasName("org.apache.dubbo.rpc.service.GenericService/$invokeAsync")
                            .hasKind(SpanKind.CLIENT)
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfying(
                                buildClientSpanAttributes(
                                    "org.apache.dubbo.rpc.service.GenericService", "$invokeAsync")),
                    span ->
                        span.hasName(
                                "io.opentelemetry.instrumentation.apachedubbo.v2_7.api.HelloService/hello")
                            .hasKind(SpanKind.SERVER)
                            .hasParent(trace.getSpan(1))
                            .hasAttributesSatisfying(attributes)));

    testing()
        .waitAndAssertMetrics(
            "io.opentelemetry.apache-dubbo-2.7",
            getServerDurationMetricName(),
            metrics ->
                metrics.anySatisfy(
                    metric ->
                        assertThat(metric)
                            .hasUnit(getDurationUnit())
                            .hasHistogramSatisfying(
                                histogram ->
                                    histogram.hasPointsSatisfying(
                                        point ->
                                            point.hasAttributesSatisfyingExactly(
                                                buildMetricAttributes(
                                                    "io.opentelemetry.instrumentation.apachedubbo.v2_7.api.HelloService",
                                                    "hello"))))));

    testing()
        .waitAndAssertMetrics(
            "io.opentelemetry.apache-dubbo-2.7",
            getClientDurationMetricName(),
            metrics ->
                metrics.anySatisfy(
                    metric ->
                        assertThat(metric)
                            .hasUnit(getDurationUnit())
                            .hasHistogramSatisfying(
                                histogram ->
                                    histogram.hasPointsSatisfying(
                                        point ->
                                            point.hasAttributesSatisfyingExactly(
                                                buildClientMetricAttributes(
                                                    "org.apache.dubbo.rpc.service.GenericService",
                                                    "$invokeAsync"))))));
  }

  static void assertNetworkType(AbstractStringAssert<?> stringAssert) {
    assertLatestDeps(
        stringAssert,
        a ->
            a.satisfiesAnyOf(
                val -> assertThat(val).isEqualTo("ipv4"),
                val -> assertThat(val).isEqualTo("ipv6")));
  }

  static void assertLatestDeps(
      AbstractAssert<?, ?> assertion, Consumer<AbstractAssert<?, ?>> action) {
    if (Boolean.getBoolean("testLatestDeps")) {
      action.accept(assertion);
    } else {
      assertion.isNull();
    }
  }
}
