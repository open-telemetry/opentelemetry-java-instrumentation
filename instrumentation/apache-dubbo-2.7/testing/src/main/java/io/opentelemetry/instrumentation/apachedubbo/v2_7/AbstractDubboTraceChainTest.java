/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v2_7;

import static io.opentelemetry.instrumentation.testing.GlobalTraceUtil.runWithSpan;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.apachedubbo.v2_7.api.HelloService;
import io.opentelemetry.instrumentation.apachedubbo.v2_7.api.MiddleService;
import io.opentelemetry.instrumentation.apachedubbo.v2_7.impl.HelloServiceImpl;
import io.opentelemetry.instrumentation.apachedubbo.v2_7.impl.MiddleServiceImpl;
import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.semconv.NetworkAttributes;
import io.opentelemetry.semconv.ServerAttributes;
import io.opentelemetry.semconv.incubating.RpcIncubatingAttributes;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.List;
import org.apache.dubbo.common.utils.NetUtils;
import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ProtocolConfig;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.RegistryConfig;
import org.apache.dubbo.config.ServiceConfig;
import org.apache.dubbo.config.bootstrap.DubboBootstrap;
import org.apache.dubbo.rpc.service.GenericService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractDubboTraceChainTest {

  private DubboBootstrap bootstrap;
  private DubboBootstrap consumerBootstrap;
  private DubboBootstrap middleBootstrap;

  @BeforeAll
  static void setUp() throws Exception {
    System.setProperty("dubbo.application.qos-enable", "false");
    Field field = NetUtils.class.getDeclaredField("LOCAL_ADDRESS");
    field.setAccessible(true);
    field.set(null, InetAddress.getLoopbackAddress());
  }

  @AfterAll
  static void setDown() {
    System.clearProperty("dubbo.application.qos-enable");
  }

  abstract InstrumentationExtension testing();

  @AfterEach
  @SuppressWarnings("CatchingUnchecked")
  public void afterEach() {
    List<DubboBootstrap> dubboBootstraps =
        Arrays.asList(bootstrap, consumerBootstrap, middleBootstrap);
    for (DubboBootstrap bootstrap : dubboBootstraps) {
      try {
        if (bootstrap != null) {
          bootstrap.destroy();
        }
      } catch (Exception e) {
        // ignore
      }
    }
  }

  ReferenceConfig<HelloService> configureClient(int port) {
    ReferenceConfig<HelloService> reference = new ReferenceConfig<>();
    reference.setInterface(HelloService.class);
    reference.setGeneric("true");
    reference.setUrl("dubbo://localhost:" + port + "/?timeout=30000");
    return reference;
  }

  ReferenceConfig<HelloService> configureLocalClient(int port) {
    ReferenceConfig<HelloService> reference = new ReferenceConfig<>();
    reference.setInterface(HelloService.class);
    reference.setGeneric("true");
    reference.setUrl("injvm://localhost:" + port + "/?timeout=30000");
    return reference;
  }

  ReferenceConfig<MiddleService> configureMiddleClient(int port) {
    ReferenceConfig<MiddleService> reference = new ReferenceConfig<>();
    reference.setInterface(MiddleService.class);
    reference.setGeneric("true");
    reference.setUrl("dubbo://localhost:" + port + "/?timeout=30000");
    return reference;
  }

  ServiceConfig<HelloService> configureServer() {
    RegistryConfig registerConfig = new RegistryConfig();
    registerConfig.setAddress("N/A");
    ServiceConfig<HelloService> service = new ServiceConfig<>();
    service.setInterface(HelloService.class);
    service.setRef(new HelloServiceImpl());
    service.setRegistry(registerConfig);
    return service;
  }

  ServiceConfig<MiddleService> configureMiddleServer(
      ReferenceConfig<HelloService> referenceConfig) {
    RegistryConfig registerConfig = new RegistryConfig();
    registerConfig.setAddress("N/A");
    ServiceConfig<MiddleService> service = new ServiceConfig<>();
    service.setInterface(MiddleService.class);
    service.setRef(new MiddleServiceImpl(referenceConfig));
    service.setRegistry(registerConfig);
    return service;
  }

  @Test
  @DisplayName("test that context is propagated correctly in chained dubbo calls")
  @SuppressWarnings({"rawtypes", "unchecked"})
  void testDubboChain() {
    int port = PortUtils.findOpenPorts(2);
    int middlePort = port + 1;

    // setup hello service provider
    ProtocolConfig protocolConfig = new ProtocolConfig();
    protocolConfig.setPort(port);

    bootstrap = DubboTestUtil.newDubboBootstrap();
    bootstrap
        .application(new ApplicationConfig("dubbo-test-provider"))
        .service(configureServer())
        .protocol(protocolConfig)
        .start();

    // setup middle service provider, hello service consumer
    ProtocolConfig middleProtocolConfig = new ProtocolConfig();
    middleProtocolConfig.setPort(middlePort);

    ReferenceConfig<HelloService> clientReference = configureClient(port);
    middleBootstrap = DubboTestUtil.newDubboBootstrap();
    middleBootstrap
        .application(new ApplicationConfig("dubbo-demo-middle"))
        .reference(clientReference)
        .service(configureMiddleServer(clientReference))
        .protocol(middleProtocolConfig)
        .start();

    // setup middle service consumer
    ProtocolConfig consumerProtocolConfig = new ProtocolConfig();
    consumerProtocolConfig.setRegister(false);

    ReferenceConfig middleReference = configureMiddleClient(middlePort);
    consumerBootstrap = DubboTestUtil.newDubboBootstrap();
    consumerBootstrap
        .application(new ApplicationConfig("dubbo-demo-api-consumer"))
        .reference(middleReference)
        .protocol(consumerProtocolConfig)
        .start();

    GenericService genericService = (GenericService) middleReference.get();

    Object[] o = new Object[1];
    o[0] = "hello";
    Object response =
        runWithSpan(
            "parent",
            () -> genericService.$invoke("hello", new String[] {String.class.getName()}, o));

    assertEquals("hello", response);
    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                    span ->
                        span.hasName("org.apache.dubbo.rpc.service.GenericService/$invoke")
                            .hasKind(SpanKind.CLIENT)
                            .hasAttributesSatisfyingExactly(
                                equalTo(RpcIncubatingAttributes.RPC_SYSTEM, "apache_dubbo"),
                                equalTo(
                                    RpcIncubatingAttributes.RPC_SERVICE,
                                    "org.apache.dubbo.rpc.service.GenericService"),
                                equalTo(RpcIncubatingAttributes.RPC_METHOD, "$invoke"),
                                equalTo(ServerAttributes.SERVER_ADDRESS, "localhost"),
                                satisfies(
                                    ServerAttributes.SERVER_PORT, k -> k.isInstanceOf(Long.class)),
                                satisfies(
                                    NetworkAttributes.NETWORK_PEER_ADDRESS,
                                    k ->
                                        k.satisfiesAnyOf(
                                            val -> assertThat(val).isNull(),
                                            val -> assertThat(val).isInstanceOf(String.class))),
                                satisfies(
                                    NetworkAttributes.NETWORK_PEER_PORT,
                                    k ->
                                        k.satisfiesAnyOf(
                                            val -> assertThat(val).isNull(),
                                            val -> assertThat(val).isInstanceOf(Long.class))),
                                satisfies(
                                    NetworkAttributes.NETWORK_TYPE,
                                    k ->
                                        k.satisfiesAnyOf(
                                            val -> assertThat(val).isNull(),
                                            val -> assertThat(val).isEqualTo("ipv4"),
                                            val -> assertThat(val).isEqualTo("ipv6")))),
                    span ->
                        span.hasName(
                                "io.opentelemetry.instrumentation.apachedubbo.v2_7.api.MiddleService/hello")
                            .hasKind(SpanKind.SERVER)
                            .hasAttributesSatisfying(
                                equalTo(RpcIncubatingAttributes.RPC_SYSTEM, "apache_dubbo"),
                                equalTo(
                                    RpcIncubatingAttributes.RPC_SERVICE,
                                    "io.opentelemetry.instrumentation.apachedubbo.v2_7.api.MiddleService"),
                                equalTo(RpcIncubatingAttributes.RPC_METHOD, "hello"),
                                satisfies(
                                    NetworkAttributes.NETWORK_PEER_ADDRESS,
                                    k -> k.isInstanceOf(String.class)),
                                satisfies(
                                    NetworkAttributes.NETWORK_PEER_PORT,
                                    k -> k.isInstanceOf(Long.class)),
                                satisfies(
                                    NetworkAttributes.NETWORK_TYPE,
                                    k ->
                                        k.satisfiesAnyOf(
                                            val -> assertThat(val).isNull(),
                                            val -> assertThat(val).isEqualTo("ipv4"),
                                            val -> assertThat(val).isEqualTo("ipv6")))),
                    span ->
                        span.hasName("org.apache.dubbo.rpc.service.GenericService/$invoke")
                            .hasKind(SpanKind.CLIENT)
                            .hasAttributesSatisfyingExactly(
                                equalTo(RpcIncubatingAttributes.RPC_SYSTEM, "apache_dubbo"),
                                equalTo(
                                    RpcIncubatingAttributes.RPC_SERVICE,
                                    "org.apache.dubbo.rpc.service.GenericService"),
                                equalTo(RpcIncubatingAttributes.RPC_METHOD, "$invoke"),
                                equalTo(ServerAttributes.SERVER_ADDRESS, "localhost"),
                                satisfies(
                                    ServerAttributes.SERVER_PORT, k -> k.isInstanceOf(Long.class)),
                                satisfies(
                                    NetworkAttributes.NETWORK_PEER_ADDRESS,
                                    k ->
                                        k.satisfiesAnyOf(
                                            val -> assertThat(val).isNull(),
                                            val -> assertThat(val).isInstanceOf(String.class))),
                                satisfies(
                                    NetworkAttributes.NETWORK_PEER_PORT,
                                    k ->
                                        k.satisfiesAnyOf(
                                            val -> assertThat(val).isNull(),
                                            val -> assertThat(val).isInstanceOf(Long.class))),
                                satisfies(
                                    NetworkAttributes.NETWORK_TYPE,
                                    k ->
                                        k.satisfiesAnyOf(
                                            val -> assertThat(val).isNull(),
                                            val -> assertThat(val).isEqualTo("ipv4"),
                                            val -> assertThat(val).isEqualTo("ipv6")))),
                    span ->
                        span.hasName(
                                "io.opentelemetry.instrumentation.apachedubbo.v2_7.api.HelloService/hello")
                            .hasKind(SpanKind.SERVER)
                            .hasAttributesSatisfying(
                                equalTo(RpcIncubatingAttributes.RPC_SYSTEM, "apache_dubbo"),
                                equalTo(
                                    RpcIncubatingAttributes.RPC_SERVICE,
                                    "io.opentelemetry.instrumentation.apachedubbo.v2_7.api.HelloService"),
                                equalTo(RpcIncubatingAttributes.RPC_METHOD, "hello"),
                                satisfies(
                                    NetworkAttributes.NETWORK_PEER_ADDRESS,
                                    k -> k.isInstanceOf(String.class)),
                                satisfies(
                                    NetworkAttributes.NETWORK_PEER_PORT,
                                    k -> k.isInstanceOf(Long.class)),
                                satisfies(
                                    NetworkAttributes.NETWORK_TYPE,
                                    k ->
                                        k.satisfiesAnyOf(
                                            val -> assertThat(val).isNull(),
                                            val -> assertThat(val).isEqualTo("ipv4"),
                                            val -> assertThat(val).isEqualTo("ipv6"))))));
  }

  @Test
  @DisplayName("test ignore injvm calls")
  @SuppressWarnings({"rawtypes", "unchecked"})
  void testDubboChainInJvm() {
    int port = PortUtils.findOpenPorts(2);
    int middlePort = port + 1;

    // setup hello service provider
    ProtocolConfig protocolConfig = new ProtocolConfig();
    protocolConfig.setPort(port);

    bootstrap = DubboTestUtil.newDubboBootstrap();
    bootstrap
        .application(new ApplicationConfig("dubbo-test-provider"))
        .service(configureServer())
        .protocol(protocolConfig)
        .start();

    // setup middle service provider, hello service consumer
    ProtocolConfig middleProtocolConfig = new ProtocolConfig();
    middleProtocolConfig.setPort(middlePort);

    ReferenceConfig<HelloService> clientReference = configureLocalClient(port);
    middleBootstrap = DubboTestUtil.newDubboBootstrap();
    middleBootstrap
        .application(new ApplicationConfig("dubbo-demo-middle"))
        .service(configureMiddleServer(clientReference))
        .protocol(middleProtocolConfig)
        .start();

    // setup middle service consumer
    ProtocolConfig consumerProtocolConfig = new ProtocolConfig();
    consumerProtocolConfig.setRegister(false);

    ReferenceConfig middleReference = configureMiddleClient(middlePort);
    consumerBootstrap = DubboTestUtil.newDubboBootstrap();
    consumerBootstrap
        .application(new ApplicationConfig("dubbo-demo-api-consumer"))
        .reference(middleReference)
        .protocol(consumerProtocolConfig)
        .start();

    GenericService genericService = (GenericService) middleReference.get();

    Object[] o = new Object[1];
    o[0] = "hello";
    Object response =
        runWithSpan(
            "parent",
            () -> genericService.$invoke("hello", new String[] {String.class.getName()}, o));

    assertEquals("hello", response);
    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                    span ->
                        span.hasName("org.apache.dubbo.rpc.service.GenericService/$invoke")
                            .hasKind(SpanKind.CLIENT)
                            .hasAttributesSatisfyingExactly(
                                equalTo(RpcIncubatingAttributes.RPC_SYSTEM, "apache_dubbo"),
                                equalTo(
                                    RpcIncubatingAttributes.RPC_SERVICE,
                                    "org.apache.dubbo.rpc.service.GenericService"),
                                equalTo(RpcIncubatingAttributes.RPC_METHOD, "$invoke"),
                                equalTo(ServerAttributes.SERVER_ADDRESS, "localhost"),
                                satisfies(
                                    ServerAttributes.SERVER_PORT, k -> k.isInstanceOf(Long.class)),
                                satisfies(
                                    NetworkAttributes.NETWORK_PEER_ADDRESS,
                                    k ->
                                        k.satisfiesAnyOf(
                                            val -> assertThat(val).isNull(),
                                            val -> assertThat(val).isInstanceOf(String.class))),
                                satisfies(
                                    NetworkAttributes.NETWORK_PEER_PORT,
                                    k ->
                                        k.satisfiesAnyOf(
                                            val -> assertThat(val).isNull(),
                                            val -> assertThat(val).isInstanceOf(Long.class))),
                                satisfies(
                                    NetworkAttributes.NETWORK_TYPE,
                                    k ->
                                        k.satisfiesAnyOf(
                                            val -> assertThat(val).isNull(),
                                            val -> assertThat(val).isEqualTo("ipv4"),
                                            val -> assertThat(val).isEqualTo("ipv6")))),
                    span ->
                        span.hasName(
                                "io.opentelemetry.instrumentation.apachedubbo.v2_7.api.MiddleService/hello")
                            .hasKind(SpanKind.SERVER)
                            .hasAttributesSatisfying(
                                equalTo(RpcIncubatingAttributes.RPC_SYSTEM, "apache_dubbo"),
                                equalTo(
                                    RpcIncubatingAttributes.RPC_SERVICE,
                                    "io.opentelemetry.instrumentation.apachedubbo.v2_7.api.MiddleService"),
                                equalTo(RpcIncubatingAttributes.RPC_METHOD, "hello"),
                                satisfies(
                                    NetworkAttributes.NETWORK_PEER_ADDRESS,
                                    k -> k.isInstanceOf(String.class)),
                                satisfies(
                                    NetworkAttributes.NETWORK_PEER_PORT,
                                    k -> k.isInstanceOf(Long.class)),
                                satisfies(
                                    NetworkAttributes.NETWORK_TYPE,
                                    k ->
                                        k.satisfiesAnyOf(
                                            val -> assertThat(val).isNull(),
                                            val -> assertThat(val).isEqualTo("ipv4"),
                                            val -> assertThat(val).isEqualTo("ipv6"))))));
  }
}
