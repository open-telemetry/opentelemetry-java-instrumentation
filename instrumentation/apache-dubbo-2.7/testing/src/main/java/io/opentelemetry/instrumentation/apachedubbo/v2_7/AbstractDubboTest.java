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
import io.opentelemetry.instrumentation.apachedubbo.v2_7.impl.HelloServiceImpl;
import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.semconv.NetworkAttributes;
import io.opentelemetry.semconv.ServerAttributes;
import io.opentelemetry.semconv.incubating.RpcIncubatingAttributes;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.apache.dubbo.common.utils.NetUtils;
import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ProtocolConfig;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.RegistryConfig;
import org.apache.dubbo.config.ServiceConfig;
import org.apache.dubbo.config.bootstrap.DubboBootstrap;
import org.apache.dubbo.rpc.service.GenericService;
import org.assertj.core.api.AbstractAssert;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractDubboTest {

  private final ProtocolConfig protocolConfig = new ProtocolConfig();

  abstract InstrumentationExtension testing();

  private DubboBootstrap bootstrap;
  private DubboBootstrap consumerBootstrap;

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

  @AfterEach
  @SuppressWarnings("CatchingUnchecked")
  public void afterEach() {
    List<DubboBootstrap> dubboBootstraps = Arrays.asList(bootstrap, consumerBootstrap);
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

  ServiceConfig<HelloServiceImpl> configureServer() {
    RegistryConfig registerConfig = new RegistryConfig();
    registerConfig.setAddress("N/A");
    ServiceConfig<HelloServiceImpl> service = new ServiceConfig<>();
    service.setInterface(HelloService.class);
    service.setRef(new HelloServiceImpl());
    service.setRegistry(registerConfig);
    return service;
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void testApacheDubboBase() {
    int port = PortUtils.findOpenPort();
    protocolConfig.setPort(port);
    // provider boostrap
    bootstrap = DubboTestUtil.newDubboBootstrap();
    bootstrap
        .application(new ApplicationConfig("dubbo-test-provider"))
        .service(configureServer())
        .protocol(protocolConfig)
        .start();

    // consumer boostrap
    consumerBootstrap = DubboTestUtil.newDubboBootstrap();
    ReferenceConfig referenceConfig = configureClient(port);
    ProtocolConfig consumerProtocolConfig = new ProtocolConfig();
    consumerProtocolConfig.setRegister(false);
    consumerBootstrap
        .application(new ApplicationConfig("dubbo-demo-api-consumer"))
        .reference(referenceConfig)
        .protocol(consumerProtocolConfig)
        .start();

    // generic call
    ReferenceConfig<GenericService> reference = referenceConfig;
    GenericService genericService = reference.get();

    Object[] o = new Object[1];
    o[0] = "hello";
    Object response =
        runWithSpan(
            "parent",
            () -> genericService.$invoke("hello", new String[] {String.class.getName()}, o));

    assertThat(response).isEqualTo("hello");
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
                                "io.opentelemetry.instrumentation.apachedubbo.v2_7.api.HelloService/hello")
                            .hasKind(SpanKind.SERVER)
                            .hasParent(trace.getSpan(1))
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
                                    NetworkAttributes.NETWORK_TYPE, AbstractAssert::isNull))));
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void testApacheDubboTest() throws ExecutionException, InterruptedException {
    int port = PortUtils.findOpenPort();
    protocolConfig.setPort(port);

    bootstrap = DubboTestUtil.newDubboBootstrap();
    bootstrap
        .application(new ApplicationConfig("dubbo-test-async-provider"))
        .service(configureServer())
        .protocol(protocolConfig)
        .start();

    ProtocolConfig consumerProtocolConfig = new ProtocolConfig();
    consumerProtocolConfig.setRegister(false);

    ReferenceConfig referenceConfig = configureClient(port);
    consumerBootstrap = DubboTestUtil.newDubboBootstrap();
    consumerBootstrap
        .application(new ApplicationConfig("dubbo-demo-async-api-consumer"))
        .reference(referenceConfig)
        .protocol(consumerProtocolConfig)
        .start();

    // generic call
    ReferenceConfig<GenericService> reference = referenceConfig;
    GenericService genericService = reference.get();
    Object[] o = new Object[1];
    o[0] = "hello";
    CompletableFuture<Object> response =
        runWithSpan(
            "parent",
            () -> genericService.$invokeAsync("hello", new String[] {String.class.getName()}, o));

    assertEquals("hello", response.get());

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                    span ->
                        span.hasName("org.apache.dubbo.rpc.service.GenericService/$invokeAsync")
                            .hasKind(SpanKind.CLIENT)
                            .hasAttributesSatisfyingExactly(
                                equalTo(RpcIncubatingAttributes.RPC_SYSTEM, "apache_dubbo"),
                                equalTo(
                                    RpcIncubatingAttributes.RPC_SERVICE,
                                    "org.apache.dubbo.rpc.service.GenericService"),
                                equalTo(RpcIncubatingAttributes.RPC_METHOD, "$invokeAsync"),
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
}
