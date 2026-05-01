/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v2_7;

import static io.opentelemetry.instrumentation.apachedubbo.v2_7.AbstractDubboTest.assertLatestDeps;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldRpcSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableRpcSemconv;
import static io.opentelemetry.instrumentation.testing.GlobalTraceUtil.runWithSpan;
import static io.opentelemetry.instrumentation.testing.junit.service.SemconvServiceStabilityUtil.maybeStablePeerService;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_TYPE;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_METHOD;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SERVICE;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SYSTEM;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SYSTEM_NAME;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.apachedubbo.v2_7.api.HelloService;
import io.opentelemetry.instrumentation.apachedubbo.v2_7.impl.HelloServiceImpl;
import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.lang.reflect.Field;
import java.net.InetAddress;
import org.apache.curator.test.TestingServer;
import org.apache.dubbo.common.utils.NetUtils;
import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ProtocolConfig;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.RegistryConfig;
import org.apache.dubbo.config.ServiceConfig;
import org.apache.dubbo.config.bootstrap.DubboBootstrap;
import org.apache.dubbo.rpc.service.GenericService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Integration test that verifies the registry-mode end-to-end flow: provider registers to
 * ZooKeeper, consumer discovers via ZooKeeper, and the {@code SERVER_ADDRESS} span attribute
 * contains the registry address (with service interface, version, and group) instead of the
 * provider host.
 */
@SuppressWarnings("deprecation") // using deprecated semconv
public abstract class AbstractDubboRegistryTest {

  private static final String SERVICE_VERSION = "1.0.0";
  private static final String SERVICE_GROUP = "testGroup";

  private static TestingServer zkServer;
  private static InetAddress originalLocalAddress;

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  protected abstract InstrumentationExtension testing();

  protected abstract boolean hasServicePeerName();

  @BeforeAll
  static void setUp() throws Exception {
    zkServer = new TestingServer();
    zkServer.start();

    System.setProperty("dubbo.application.qos-enable", "false");
    Field field = NetUtils.class.getDeclaredField("LOCAL_ADDRESS");
    field.setAccessible(true);
    originalLocalAddress = (InetAddress) field.get(null);
    field.set(null, InetAddress.getLoopbackAddress());
  }

  @AfterAll
  static void tearDown() throws Exception {
    System.clearProperty("dubbo.application.qos-enable");
    if (originalLocalAddress != null) {
      Field field = NetUtils.class.getDeclaredField("LOCAL_ADDRESS");
      field.setAccessible(true);
      field.set(null, originalLocalAddress);
    }
    if (zkServer != null) {
      zkServer.close();
    }
  }

  private static String zkAddress() {
    return "zookeeper://127.0.0.1:" + zkServer.getPort();
  }

  private static RegistryConfig newZkRegistryConfig(String zkAddr) {
    RegistryConfig config = new RegistryConfig();
    config.setAddress(zkAddr);
    config.setUseAsConfigCenter(false);
    config.setUseAsMetadataCenter(false);
    return config;
  }

  @Test
  void testRegistryModeServerAddress() throws Exception {
    int port = PortUtils.findOpenPort();
    String zkAddr = zkAddress();

    ProtocolConfig protocolConfig = new ProtocolConfig();
    protocolConfig.setPort(port);

    ServiceConfig<HelloServiceImpl> service = new ServiceConfig<>();
    service.setInterface(HelloService.class);
    service.setRef(new HelloServiceImpl());
    service.setVersion(SERVICE_VERSION);
    service.setGroup(SERVICE_GROUP);

    DubboBootstrap providerBootstrap = DubboTestUtil.newDubboBootstrap();
    cleanup.deferCleanup(providerBootstrap::destroy);
    providerBootstrap
        .application(new ApplicationConfig("dubbo-registry-test-provider"))
        .registry(newZkRegistryConfig(zkAddr))
        .service(service)
        .protocol(protocolConfig)
        .start();

    // --- consumer: discover from ZooKeeper (must disable injvm to avoid same-JVM shortcut) ---
    ReferenceConfig<HelloService> referenceConfig = new ReferenceConfig<>();
    referenceConfig.setInterface(HelloService.class);
    referenceConfig.setGeneric("true");
    referenceConfig.setVersion(SERVICE_VERSION);
    referenceConfig.setGroup(SERVICE_GROUP);
    referenceConfig.setTimeout(30000);
    referenceConfig.setInjvm(false);

    ProtocolConfig consumerProtocol = new ProtocolConfig();
    consumerProtocol.setRegister(false);

    DubboBootstrap consumerBootstrap = DubboTestUtil.newDubboBootstrap();
    cleanup.deferCleanup(consumerBootstrap::destroy);
    consumerBootstrap
        .application(new ApplicationConfig("dubbo-registry-test-consumer"))
        .registry(newZkRegistryConfig(zkAddr))
        .reference(referenceConfig)
        .protocol(consumerProtocol)
        .start();

    @SuppressWarnings({"rawtypes", "unchecked"})
    ReferenceConfig<GenericService> reference = (ReferenceConfig) referenceConfig;
    GenericService genericService = reference.get();

    Object response =
        runWithSpan(
            "parent",
            () ->
                genericService.$invoke(
                    "hello", new String[] {String.class.getName()}, new Object[] {"hello"}));

    assertThat(response).isEqualTo("hello");

    // In registry mode, SERVER_ADDRESS = "registryProtocol://host:port/interface:version:group"
    // and SERVER_PORT is absent (null).
    // See https://github.com/open-telemetry/semantic-conventions/pull/3317
    String expectedServiceTarget =
        HelloService.class.getName() + ":" + SERVICE_VERSION + ":" + SERVICE_GROUP;
    String expectedServerAddress = zkAddr + "/" + expectedServiceTarget;

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                    span ->
                        span.hasName("org.apache.dubbo.rpc.service.GenericService/$invoke")
                            .hasKind(SpanKind.CLIENT)
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfyingExactly(
                                equalTo(RPC_SYSTEM, emitOldRpcSemconv() ? "apache_dubbo" : null),
                                equalTo(RPC_SYSTEM_NAME, emitStableRpcSemconv() ? "dubbo" : null),
                                equalTo(
                                    RPC_SERVICE,
                                    emitOldRpcSemconv()
                                        ? "org.apache.dubbo.rpc.service.GenericService"
                                        : null),
                                equalTo(
                                    RPC_METHOD,
                                    emitStableRpcSemconv()
                                        ? "org.apache.dubbo.rpc.service.GenericService/$invoke"
                                        : "$invoke"),
                                equalTo(SERVER_ADDRESS, expectedServerAddress),
                                equalTo(SERVER_PORT, null),
                                satisfies(
                                    NETWORK_PEER_ADDRESS,
                                    val ->
                                        assertLatestDeps(val, v -> v.isInstanceOf(String.class))),
                                satisfies(
                                    NETWORK_PEER_PORT,
                                    val -> assertLatestDeps(val, v -> v.isInstanceOf(Long.class))),
                                satisfies(NETWORK_TYPE, AbstractDubboTest::assertNetworkType)),
                    span ->
                        span.hasName(
                                "io.opentelemetry.instrumentation.apachedubbo.v2_7.api.HelloService/hello")
                            .hasKind(SpanKind.SERVER)
                            .hasParent(trace.getSpan(1))
                            .hasAttributesSatisfyingExactly(
                                equalTo(RPC_SYSTEM, emitOldRpcSemconv() ? "apache_dubbo" : null),
                                equalTo(RPC_SYSTEM_NAME, emitStableRpcSemconv() ? "dubbo" : null),
                                equalTo(
                                    RPC_SERVICE,
                                    emitOldRpcSemconv()
                                        ? "io.opentelemetry.instrumentation.apachedubbo.v2_7.api.HelloService"
                                        : null),
                                equalTo(
                                    RPC_METHOD,
                                    emitStableRpcSemconv()
                                        ? "io.opentelemetry.instrumentation.apachedubbo.v2_7.api.HelloService/hello"
                                        : "hello"),
                                equalTo(
                                    maybeStablePeerService(),
                                    hasServicePeerName() && Boolean.getBoolean("testLatestDeps")
                                        ? "test-peer-service"
                                        : null),
                                satisfies(
                                    NETWORK_PEER_ADDRESS, val -> val.isInstanceOf(String.class)),
                                satisfies(
                                    NETWORK_PEER_PORT, val -> val.isInstanceOf(Long.class)))));
  }
}
