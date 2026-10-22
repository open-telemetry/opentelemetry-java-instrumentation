/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v2_7;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.instrumentation.testing.GlobalTraceUtil.runWithSpan;
import static io.opentelemetry.instrumentation.testing.junit.rpc.SemconvRpcStabilityUtil.emitOldRpcSemconv;
import static io.opentelemetry.instrumentation.testing.junit.rpc.SemconvRpcStabilityUtil.emitPreviewRpcSemconv;
import static io.opentelemetry.instrumentation.testing.junit.service.SemconvServiceStabilityUtil.maybeStablePeerService;
import static io.opentelemetry.instrumentation.testing.util.TestLatestDeps.testLatestDeps;
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
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_METHOD_ORIGINAL;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SERVICE;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SYSTEM;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SYSTEM_NAME;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.apachedubbo.v2_7.api.HelloService;
import io.opentelemetry.instrumentation.apachedubbo.v2_7.api.MiddleService;
import io.opentelemetry.instrumentation.apachedubbo.v2_7.impl.HelloServiceImpl;
import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import org.apache.dubbo.common.utils.NetUtils;
import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ProtocolConfig;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.RegistryConfig;
import org.apache.dubbo.config.ServiceConfig;
import org.apache.dubbo.config.bootstrap.DubboBootstrap;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.service.GenericException;
import org.apache.dubbo.rpc.service.GenericService;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.AbstractStringAssert;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

@SuppressWarnings("deprecation") // using deprecated semconv
public abstract class AbstractDubboTest {

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  private final ProtocolConfig protocolConfig = new ProtocolConfig();

  protected abstract InstrumentationExtension testing();

  protected boolean canCaptureUnknownServiceSpans() {
    return false;
  }

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
    return configureServer(new HelloServiceImpl());
  }

  ServiceConfig<HelloServiceImpl> configureServer(HelloServiceImpl implementation) {
    RegistryConfig registerConfig = new RegistryConfig();
    registerConfig.setAddress("N/A");
    ServiceConfig<HelloServiceImpl> service = new ServiceConfig<>();
    service.setInterface(HelloService.class);
    service.setRef(implementation);
    service.setRegistry(registerConfig);
    return service;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  ReferenceConfig<GenericService> convertReference(ReferenceConfig<HelloService> config) {
    return (ReferenceConfig) config;
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
                            .hasAttributesSatisfyingExactly(
                                equalTo(RPC_SYSTEM, emitOldRpcSemconv() ? "apache_dubbo" : null),
                                equalTo(RPC_SYSTEM_NAME, emitPreviewRpcSemconv() ? "dubbo" : null),
                                equalTo(
                                    RPC_SERVICE,
                                    emitOldRpcSemconv()
                                        ? "org.apache.dubbo.rpc.service.GenericService"
                                        : null),
                                equalTo(
                                    RPC_METHOD,
                                    emitPreviewRpcSemconv()
                                        ? "org.apache.dubbo.rpc.service.GenericService/$invoke"
                                        : "$invoke"),
                                equalTo(maybeStablePeerService(), testing().expectedPeerService()),
                                equalTo(SERVER_ADDRESS, "localhost"),
                                satisfies(SERVER_PORT, val -> val.isInstanceOf(Long.class)),
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
                                equalTo(RPC_SYSTEM_NAME, emitPreviewRpcSemconv() ? "dubbo" : null),
                                equalTo(
                                    RPC_SERVICE,
                                    emitOldRpcSemconv()
                                        ? "io.opentelemetry.instrumentation.apachedubbo.v2_7.api.HelloService"
                                        : null),
                                equalTo(
                                    RPC_METHOD,
                                    emitPreviewRpcSemconv()
                                        ? "io.opentelemetry.instrumentation.apachedubbo.v2_7.api.HelloService/hello"
                                        : "hello"),
                                equalTo(
                                    maybeStablePeerService(),
                                    testLatestDeps() ? testing().expectedPeerService() : null),
                                satisfies(
                                    NETWORK_PEER_ADDRESS, val -> val.isInstanceOf(String.class)),
                                satisfies(
                                    NETWORK_PEER_PORT, val -> val.isInstanceOf(Long.class)))));

    if (emitOldRpcSemconv()) {
      testing()
          .waitAndAssertMetrics(
              "io.opentelemetry.apache-dubbo-2.7",
              metric ->
                  metric
                      .hasName("rpc.server.duration")
                      .hasUnit("ms")
                      .hasHistogramSatisfying(
                          histogram ->
                              histogram.hasPointsSatisfying(
                                  point ->
                                      point.hasAttributesSatisfyingExactly(
                                          equalTo(RPC_SYSTEM, "apache_dubbo"),
                                          equalTo(
                                              RPC_SERVICE,
                                              "io.opentelemetry.instrumentation.apachedubbo.v2_7.api.HelloService"),
                                          equalTo(RPC_METHOD, "hello")))));

      testing()
          .waitAndAssertMetrics(
              "io.opentelemetry.apache-dubbo-2.7",
              metric ->
                  metric
                      .hasName("rpc.client.duration")
                      .hasUnit("ms")
                      .hasHistogramSatisfying(
                          histogram ->
                              histogram.hasPointsSatisfying(
                                  point ->
                                      point.hasAttributesSatisfyingExactly(
                                          equalTo(RPC_SYSTEM, "apache_dubbo"),
                                          equalTo(
                                              RPC_SERVICE,
                                              "org.apache.dubbo.rpc.service.GenericService"),
                                          equalTo(RPC_METHOD, "$invoke"),
                                          equalTo(SERVER_ADDRESS, "localhost"),
                                          satisfies(
                                              SERVER_PORT, val -> val.isInstanceOf(Long.class)),
                                          satisfies(
                                              NETWORK_TYPE,
                                              AbstractDubboTest::assertNetworkType)))));
    }

    if (emitPreviewRpcSemconv()) {
      testing()
          .waitAndAssertMetrics(
              "io.opentelemetry.apache-dubbo-2.7",
              metric ->
                  metric
                      .hasName("rpc.server.call.duration")
                      .hasUnit("s")
                      .hasHistogramSatisfying(
                          histogram ->
                              histogram.hasPointsSatisfying(
                                  point ->
                                      point.hasAttributesSatisfyingExactly(
                                          equalTo(RPC_SYSTEM_NAME, "dubbo"),
                                          equalTo(
                                              RPC_METHOD,
                                              "io.opentelemetry.instrumentation.apachedubbo.v2_7.api.HelloService/hello")))));

      testing()
          .waitAndAssertMetrics(
              "io.opentelemetry.apache-dubbo-2.7",
              metric ->
                  metric
                      .hasName("rpc.client.call.duration")
                      .hasUnit("s")
                      .hasHistogramSatisfying(
                          histogram ->
                              histogram.hasPointsSatisfying(
                                  point ->
                                      point.hasAttributesSatisfyingExactly(
                                          equalTo(RPC_SYSTEM_NAME, "dubbo"),
                                          equalTo(
                                              RPC_METHOD,
                                              "org.apache.dubbo.rpc.service.GenericService/$invoke"),
                                          equalTo(SERVER_ADDRESS, "localhost"),
                                          satisfies(
                                              SERVER_PORT, val -> val.isInstanceOf(Long.class))))));
    }
  }

  @Test
  void testApacheDubboTest() throws ReflectiveOperationException {
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

    assertThat(response.join()).isEqualTo("hello");

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                    span ->
                        span.hasName("org.apache.dubbo.rpc.service.GenericService/$invokeAsync")
                            .hasKind(SpanKind.CLIENT)
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfyingExactly(
                                equalTo(RPC_SYSTEM, emitOldRpcSemconv() ? "apache_dubbo" : null),
                                equalTo(RPC_SYSTEM_NAME, emitPreviewRpcSemconv() ? "dubbo" : null),
                                equalTo(
                                    RPC_SERVICE,
                                    emitOldRpcSemconv()
                                        ? "org.apache.dubbo.rpc.service.GenericService"
                                        : null),
                                equalTo(
                                    RPC_METHOD,
                                    emitPreviewRpcSemconv()
                                        ? "org.apache.dubbo.rpc.service.GenericService/$invokeAsync"
                                        : "$invokeAsync"),
                                equalTo(maybeStablePeerService(), testing().expectedPeerService()),
                                equalTo(SERVER_ADDRESS, "localhost"),
                                satisfies(SERVER_PORT, val -> val.isInstanceOf(Long.class)),
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
                                equalTo(RPC_SYSTEM_NAME, emitPreviewRpcSemconv() ? "dubbo" : null),
                                equalTo(
                                    RPC_SERVICE,
                                    emitOldRpcSemconv()
                                        ? "io.opentelemetry.instrumentation.apachedubbo.v2_7.api.HelloService"
                                        : null),
                                equalTo(
                                    RPC_METHOD,
                                    emitPreviewRpcSemconv()
                                        ? "io.opentelemetry.instrumentation.apachedubbo.v2_7.api.HelloService/hello"
                                        : "hello"),
                                equalTo(
                                    maybeStablePeerService(),
                                    testLatestDeps() ? testing().expectedPeerService() : null),
                                satisfies(
                                    NETWORK_PEER_ADDRESS, val -> val.isInstanceOf(String.class)),
                                satisfies(NETWORK_PEER_PORT, val -> val.isInstanceOf(Long.class)),
                                // this attribute is not filled reliably, it is either null or
                                // "ipv4"/"ipv6"
                                satisfies(
                                    NETWORK_TYPE,
                                    val ->
                                        assertLatestDeps(
                                            val,
                                            v ->
                                                v.satisfiesAnyOf(
                                                    a -> assertThat(a).isNull(),
                                                    a -> assertThat(a).isEqualTo("ipv4"),
                                                    a -> assertThat(a).isEqualTo("ipv6")))))));

    if (emitOldRpcSemconv()) {
      testing()
          .waitAndAssertMetrics(
              "io.opentelemetry.apache-dubbo-2.7",
              metric ->
                  metric
                      .hasName("rpc.server.duration")
                      .hasUnit("ms")
                      .hasHistogramSatisfying(
                          histogram ->
                              histogram.hasPointsSatisfying(
                                  point ->
                                      point.hasAttributesSatisfyingExactly(
                                          equalTo(RPC_SYSTEM, "apache_dubbo"),
                                          equalTo(
                                              RPC_SERVICE,
                                              "io.opentelemetry.instrumentation.apachedubbo.v2_7.api.HelloService"),
                                          equalTo(RPC_METHOD, "hello")))));

      testing()
          .waitAndAssertMetrics(
              "io.opentelemetry.apache-dubbo-2.7",
              metric ->
                  metric
                      .hasName("rpc.client.duration")
                      .hasUnit("ms")
                      .hasHistogramSatisfying(
                          histogram ->
                              histogram.hasPointsSatisfying(
                                  point ->
                                      point.hasAttributesSatisfyingExactly(
                                          equalTo(RPC_SYSTEM, "apache_dubbo"),
                                          equalTo(
                                              RPC_SERVICE,
                                              "org.apache.dubbo.rpc.service.GenericService"),
                                          equalTo(RPC_METHOD, "$invokeAsync"),
                                          equalTo(SERVER_ADDRESS, "localhost"),
                                          satisfies(
                                              SERVER_PORT, val -> val.isInstanceOf(Long.class)),
                                          satisfies(
                                              NETWORK_TYPE,
                                              AbstractDubboTest::assertNetworkType)))));
    }

    if (emitPreviewRpcSemconv()) {
      testing()
          .waitAndAssertMetrics(
              "io.opentelemetry.apache-dubbo-2.7",
              metric ->
                  metric
                      .hasName("rpc.server.call.duration")
                      .hasUnit("s")
                      .hasHistogramSatisfying(
                          histogram ->
                              histogram.hasPointsSatisfying(
                                  point ->
                                      point.hasAttributesSatisfyingExactly(
                                          equalTo(RPC_SYSTEM_NAME, "dubbo"),
                                          equalTo(
                                              RPC_METHOD,
                                              "io.opentelemetry.instrumentation.apachedubbo.v2_7.api.HelloService/hello")))));

      testing()
          .waitAndAssertMetrics(
              "io.opentelemetry.apache-dubbo-2.7",
              metric ->
                  metric
                      .hasName("rpc.client.call.duration")
                      .hasUnit("s")
                      .hasHistogramSatisfying(
                          histogram ->
                              histogram.hasPointsSatisfying(
                                  point ->
                                      point.hasAttributesSatisfyingExactly(
                                          equalTo(RPC_SYSTEM_NAME, "dubbo"),
                                          equalTo(
                                              RPC_METHOD,
                                              "org.apache.dubbo.rpc.service.GenericService/$invokeAsync"),
                                          equalTo(SERVER_ADDRESS, "localhost"),
                                          satisfies(
                                              SERVER_PORT, val -> val.isInstanceOf(Long.class))))));
    }
  }

  @Test
  void testRpcExceptionFailure() throws ReflectiveOperationException {
    assertFailure(
        new RpcException(RpcException.NETWORK_EXCEPTION), false, RpcException.class.getName());
  }

  @Test
  void testAsyncRpcExceptionFailure() throws ReflectiveOperationException {
    assertFailure(
        new RpcException(RpcException.TIMEOUT_EXCEPTION), true, RpcException.class.getName());
  }

  @Test
  void testOrdinaryExceptionFailure() throws ReflectiveOperationException {
    assertFailure(
        new IllegalStateException("expected failure"),
        false,
        IllegalStateException.class.getName());
  }

  @Test
  void testAsyncClientTransportTimeout()
      throws ReflectiveOperationException,
          InterruptedException,
          ExecutionException,
          TimeoutException {
    int port = PortUtils.findOpenPort();
    protocolConfig.setPort(port);

    CountDownLatch serverCallStarted = new CountDownLatch(1);
    CountDownLatch continueServerCall = new CountDownLatch(1);
    DubboBootstrap bootstrap = DubboTestUtil.newDubboBootstrap();
    cleanup.deferCleanup(bootstrap::destroy);
    bootstrap
        .application(new ApplicationConfig("dubbo-test-timeout-provider"))
        .service(
            configureServer(
                new HelloServiceImpl() {
                  @Override
                  public String hello(String hello) {
                    serverCallStarted.countDown();
                    try {
                      continueServerCall.await();
                    } catch (InterruptedException e) {
                      Thread.currentThread().interrupt();
                      throw new IllegalStateException(e);
                    }
                    return hello;
                  }
                }))
        .protocol(protocolConfig)
        .start();

    ProtocolConfig consumerProtocolConfig = new ProtocolConfig();
    consumerProtocolConfig.setRegister(false);
    ReferenceConfig<HelloService> referenceConfig = configureClient(port);
    referenceConfig.setTimeout(500);
    referenceConfig.setRetries(0);
    DubboBootstrap consumerBootstrap = DubboTestUtil.newDubboBootstrap();
    cleanup.deferCleanup(consumerBootstrap::destroy);
    consumerBootstrap
        .application(new ApplicationConfig("dubbo-test-timeout-consumer"))
        .reference(referenceConfig)
        .protocol(consumerProtocolConfig)
        .start();

    GenericService genericService = convertReference(referenceConfig).get();
    CompletableFuture<Object> response =
        genericService.$invokeAsync(
            "hello", new String[] {String.class.getName()}, new Object[] {"hello"});

    String clientErrorType;
    try {
      assertThat(serverCallStarted.await(10, SECONDS)).isTrue();
      Throwable clientError = response.handle((value, error) -> error).get(10, SECONDS);
      assertThat(clientError).isNotNull();
      while (clientError instanceof CompletionException && clientError.getCause() != null) {
        clientError = clientError.getCause();
      }
      clientErrorType = clientError.getClass().getName();
    } finally {
      continueServerCall.countDown();
    }

    String clientMethod = "org.apache.dubbo.rpc.service.GenericService/$invokeAsync";
    String serverMethod =
        "io.opentelemetry.instrumentation.apachedubbo.v2_7.api.HelloService/hello";
    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName(clientMethod)
                            .hasKind(SpanKind.CLIENT)
                            .hasStatus(StatusData.error())
                            .hasAttributesSatisfying(
                                equalTo(
                                    ERROR_TYPE, emitPreviewRpcSemconv() ? clientErrorType : null),
                                equalTo(stringKey("rpc.response.status_code"), null)),
                    span ->
                        span.hasName(serverMethod)
                            .hasKind(SpanKind.SERVER)
                            .hasStatus(StatusData.unset())
                            .hasAttributesSatisfying(
                                equalTo(ERROR_TYPE, null),
                                equalTo(stringKey("rpc.response.status_code"), null))));

    if (emitPreviewRpcSemconv()) {
      assertPreviewErrorMetric("rpc.client.call.duration", clientMethod, clientErrorType);
      assertPreviewErrorMetric("rpc.server.call.duration", serverMethod, null);
    }
  }

  private void assertFailure(RuntimeException failure, boolean asynchronous, String serverErrorType)
      throws ReflectiveOperationException {
    int port = PortUtils.findOpenPort();
    protocolConfig.setPort(port);

    DubboBootstrap bootstrap = DubboTestUtil.newDubboBootstrap();
    cleanup.deferCleanup(bootstrap::destroy);
    bootstrap
        .application(new ApplicationConfig("dubbo-test-failure-provider"))
        .service(
            configureServer(
                new HelloServiceImpl() {
                  @Override
                  public String hello(String hello) {
                    throw failure;
                  }
                }))
        .protocol(protocolConfig)
        .start();

    ProtocolConfig consumerProtocolConfig = new ProtocolConfig();
    consumerProtocolConfig.setRegister(false);
    ReferenceConfig<HelloService> referenceConfig = configureClient(port);
    referenceConfig.setRetries(0);
    DubboBootstrap consumerBootstrap = DubboTestUtil.newDubboBootstrap();
    cleanup.deferCleanup(consumerBootstrap::destroy);
    consumerBootstrap
        .application(new ApplicationConfig("dubbo-test-failure-consumer"))
        .reference(referenceConfig)
        .protocol(consumerProtocolConfig)
        .start();

    GenericService genericService = convertReference(referenceConfig).get();
    if (asynchronous) {
      CompletableFuture<Object> response =
          genericService.$invokeAsync(
              "hello", new String[] {String.class.getName()}, new Object[] {"hello"});
      assertThatThrownBy(response::join).isInstanceOf(CompletionException.class);
    } else {
      assertThatThrownBy(
              () ->
                  genericService.$invoke(
                      "hello", new String[] {String.class.getName()}, new Object[] {"hello"}))
          .isInstanceOf(RuntimeException.class);
    }

    assertFailureTelemetry(
        "org.apache.dubbo.rpc.service.GenericService/"
            + (asynchronous ? "$invokeAsync" : "$invoke"),
        testLatestDeps()
            ? "com.alibaba.dubbo.rpc.service.GenericException"
            : GenericException.class.getName(),
        serverErrorType,
        true);
  }

  private void assertFailureTelemetry(
      String clientMethod,
      String clientErrorType,
      String serverErrorType,
      boolean serverStatusIsError) {
    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName(clientMethod)
                            .hasKind(SpanKind.CLIENT)
                            .hasStatus(StatusData.error())
                            .hasAttributesSatisfying(
                                equalTo(
                                    ERROR_TYPE, emitPreviewRpcSemconv() ? clientErrorType : null),
                                equalTo(stringKey("rpc.response.status_code"), null)),
                    span ->
                        span.hasName(
                                "io.opentelemetry.instrumentation.apachedubbo.v2_7.api.HelloService/hello")
                            .hasKind(SpanKind.SERVER)
                            .hasStatus(
                                emitPreviewRpcSemconv() && !serverStatusIsError
                                    ? StatusData.unset()
                                    : StatusData.error())
                            .hasAttributesSatisfying(
                                equalTo(
                                    ERROR_TYPE, emitPreviewRpcSemconv() ? serverErrorType : null),
                                equalTo(stringKey("rpc.response.status_code"), null))));

    if (emitPreviewRpcSemconv()) {
      assertPreviewErrorMetric(
          "rpc.server.call.duration",
          "io.opentelemetry.instrumentation.apachedubbo.v2_7.api.HelloService/hello",
          serverErrorType);
      assertPreviewErrorMetric("rpc.client.call.duration", clientMethod, clientErrorType);
    }
  }

  @Test
  void testTripleInternalStatusFailure() throws ReflectiveOperationException {
    assertTripleStatusFailure("INTERNAL", "INTERNAL", "INTERNAL", true);
  }

  @Test
  void testTripleInvalidArgumentStatus() throws ReflectiveOperationException {
    assertTripleStatusFailure("INVALID_ARGUMENT", "INVALID_ARGUMENT", null, false);
  }

  private void assertTripleStatusFailure(
      String statusCode,
      String clientErrorType,
      String serverErrorType,
      boolean serverStatusIsError)
      throws ReflectiveOperationException {
    Assumptions.assumeTrue(testLatestDeps(), "Triple protocol requires Dubbo 3.x");

    int port = PortUtils.findOpenPort();
    protocolConfig.setName("tri");
    protocolConfig.setPort(port);
    RuntimeException failure = tripleStatusException(statusCode);

    DubboBootstrap bootstrap = DubboTestUtil.newDubboBootstrap();
    cleanup.deferCleanup(bootstrap::destroy);
    bootstrap
        .application(new ApplicationConfig("dubbo-test-triple-failure-provider"))
        .service(
            configureServer(
                new HelloServiceImpl() {
                  @Override
                  public String hello(String hello) {
                    throw failure;
                  }
                }))
        .protocol(protocolConfig)
        .start();

    ReferenceConfig<HelloService> referenceConfig = new ReferenceConfig<>();
    referenceConfig.setInterface(HelloService.class);
    referenceConfig.setRetries(0);
    referenceConfig.setUrl("tri://localhost:" + port + "/?timeout=30000");
    ProtocolConfig consumerProtocolConfig = new ProtocolConfig();
    consumerProtocolConfig.setRegister(false);
    DubboBootstrap consumerBootstrap = DubboTestUtil.newDubboBootstrap();
    cleanup.deferCleanup(consumerBootstrap::destroy);
    consumerBootstrap
        .application(new ApplicationConfig("dubbo-test-triple-failure-consumer"))
        .reference(referenceConfig)
        .protocol(consumerProtocolConfig)
        .start();

    HelloService service = referenceConfig.get();
    assertThatThrownBy(() -> service.hello("hello")).isInstanceOf(RuntimeException.class);

    assertFailureTelemetry(
        "io.opentelemetry.instrumentation.apachedubbo.v2_7.api.HelloService/hello",
        clientErrorType,
        serverErrorType,
        serverStatusIsError);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static RuntimeException tripleStatusException(String statusCode)
      throws ReflectiveOperationException {
    Class<?> codeClass = Class.forName("org.apache.dubbo.rpc.TriRpcStatus$Code");
    Object code = Enum.valueOf((Class) codeClass, statusCode);
    Class<?> statusClass = Class.forName("org.apache.dubbo.rpc.TriRpcStatus");
    Object status = statusClass.getMethod("fromCode", codeClass).invoke(null, code);
    return (RuntimeException) statusClass.getMethod("asException").invoke(status);
  }

  private void assertPreviewErrorMetric(String metricName, String method, String errorType) {
    testing()
        .waitAndAssertMetrics(
            "io.opentelemetry.apache-dubbo-2.7",
            metric ->
                metric
                    .hasName(metricName)
                    .hasUnit("s")
                    .hasHistogramSatisfying(
                        histogram ->
                            histogram.hasPointsSatisfying(
                                point -> {
                                  if (errorType == null) {
                                    point.hasAttributesSatisfying(
                                        equalTo(RPC_SYSTEM_NAME, "dubbo"),
                                        equalTo(RPC_METHOD, method),
                                        equalTo(ERROR_TYPE, null),
                                        equalTo(stringKey("rpc.response.status_code"), null));
                                  } else {
                                    point.hasAttributesSatisfying(
                                        equalTo(RPC_SYSTEM_NAME, "dubbo"),
                                        equalTo(RPC_METHOD, method),
                                        equalTo(ERROR_TYPE, errorType),
                                        equalTo(stringKey("rpc.response.status_code"), null));
                                  }
                                })));
  }

  @Test
  void testUnknownService() throws ReflectiveOperationException {
    int port = PortUtils.findOpenPort();
    protocolConfig.setPort(port);

    DubboBootstrap bootstrap = DubboTestUtil.newDubboBootstrap();
    cleanup.deferCleanup(bootstrap::destroy);
    bootstrap
        .application(new ApplicationConfig("dubbo-test-unknown-provider"))
        .service(configureServer())
        .protocol(protocolConfig)
        .start();

    ReferenceConfig<MiddleService> unknownRef = new ReferenceConfig<>();
    unknownRef.setInterface(MiddleService.class);
    unknownRef.setGeneric("true");
    unknownRef.setRetries(0);
    unknownRef.setUrl("dubbo://localhost:" + port + "/?timeout=30000");

    ProtocolConfig consumerProtocolConfig = new ProtocolConfig();
    consumerProtocolConfig.setRegister(false);

    DubboBootstrap consumerBootstrap = DubboTestUtil.newDubboBootstrap();
    cleanup.deferCleanup(consumerBootstrap::destroy);
    consumerBootstrap
        .application(new ApplicationConfig("dubbo-test-unknown-consumer"))
        .reference(unknownRef)
        .protocol(consumerProtocolConfig)
        .start();

    @SuppressWarnings({"rawtypes", "unchecked"})
    ReferenceConfig<GenericService> reference = (ReferenceConfig) unknownRef;
    GenericService genericService = reference.get();

    try {
      genericService.$invoke("hello", new String[] {String.class.getName()}, new Object[] {"test"});
    } catch (Throwable ignored) {
      // expected: service not exported on provider
    }

    Consumer<SpanDataAssert> clientSpanAssert =
        span ->
            span.hasName("org.apache.dubbo.rpc.service.GenericService/$invoke")
                .hasKind(SpanKind.CLIENT)
                .hasNoParent()
                .hasStatus(StatusData.error())
                .hasAttributesSatisfyingExactly(
                    equalTo(RPC_SYSTEM, emitOldRpcSemconv() ? "apache_dubbo" : null),
                    equalTo(RPC_SYSTEM_NAME, emitPreviewRpcSemconv() ? "dubbo" : null),
                    equalTo(
                        RPC_SERVICE,
                        emitOldRpcSemconv() ? "org.apache.dubbo.rpc.service.GenericService" : null),
                    equalTo(
                        RPC_METHOD,
                        emitPreviewRpcSemconv()
                            ? "org.apache.dubbo.rpc.service.GenericService/$invoke"
                            : "$invoke"),
                    satisfies(ERROR_TYPE, AbstractDubboTest::assertErrorType),
                    equalTo(maybeStablePeerService(), testing().expectedPeerService()),
                    equalTo(SERVER_ADDRESS, "localhost"),
                    satisfies(SERVER_PORT, val -> val.isInstanceOf(Long.class)),
                    satisfies(
                        NETWORK_PEER_ADDRESS,
                        val -> assertLatestDeps(val, v -> v.isInstanceOf(String.class))),
                    satisfies(
                        NETWORK_PEER_PORT,
                        val -> assertLatestDeps(val, v -> v.isInstanceOf(Long.class))),
                    satisfies(NETWORK_TYPE, AbstractDubboTest::assertNetworkType));

    if (canCaptureUnknownServiceSpans() && emitPreviewRpcSemconv() && testLatestDeps()) {
      // Dubbo protocol (binary) with newer Dubbo versions (3.x / latest):
      // DecodeableRpcInvocation.decode() fails at PermittedSerializationKeeper before attachments
      // (which carry traceparent) are read from the wire. The server _OTHER span cannot extract
      // trace context, so it becomes a root span in a separate (disconnected) trace.
      testing()
          .waitAndAssertTraces(
              trace -> trace.hasSpansSatisfyingExactly(clientSpanAssert),
              trace ->
                  trace.hasSpansSatisfyingExactly(
                      span ->
                          span.hasName("_OTHER")
                              .hasKind(SpanKind.SERVER)
                              .hasNoParent()
                              .hasStatus(StatusData.error())
                              .hasAttributesSatisfyingExactly(
                                  equalTo(RPC_SYSTEM, emitOldRpcSemconv() ? "apache_dubbo" : null),
                                  equalTo(RPC_SYSTEM_NAME, "dubbo"),
                                  equalTo(RPC_METHOD, "_OTHER"),
                                  equalTo(
                                      RPC_METHOD_ORIGINAL,
                                      "io.opentelemetry.instrumentation.apachedubbo.v2_7.api.MiddleService/$invoke"),
                                  satisfies(ERROR_TYPE, val -> val.isNotNull()),
                                  satisfies(
                                      NETWORK_PEER_ADDRESS, val -> val.isInstanceOf(String.class)),
                                  satisfies(NETWORK_PEER_PORT, val -> val.isInstanceOf(Long.class)),
                                  satisfies(
                                      NETWORK_TYPE,
                                      AbstractDubboTest::assertUnknownServiceNetworkType))));
    } else if (canCaptureUnknownServiceSpans() && emitPreviewRpcSemconv()) {
      // Dubbo protocol (binary) with older Dubbo versions (2.7.x):
      // decode succeeds past PermittedSerializationKeeper (not present or not enforced),
      // DubboProtocol.getInvoker() fails, but attachments (with traceparent) have already been
      // read from the wire. The server _OTHER span is a child of the client span (1 trace).
      testing()
          .waitAndAssertTraces(
              trace ->
                  trace.hasSpansSatisfyingExactly(
                      clientSpanAssert,
                      span ->
                          span.hasName("_OTHER")
                              .hasKind(SpanKind.SERVER)
                              .hasParent(trace.getSpan(0))
                              .hasStatus(StatusData.error())
                              .hasAttributesSatisfyingExactly(
                                  equalTo(RPC_SYSTEM, emitOldRpcSemconv() ? "apache_dubbo" : null),
                                  equalTo(RPC_SYSTEM_NAME, "dubbo"),
                                  equalTo(RPC_METHOD, "_OTHER"),
                                  equalTo(
                                      RPC_METHOD_ORIGINAL,
                                      "io.opentelemetry.instrumentation.apachedubbo.v2_7.api.MiddleService/$invoke"),
                                  satisfies(ERROR_TYPE, val -> val.isNotNull()),
                                  satisfies(
                                      NETWORK_PEER_ADDRESS, val -> val.isInstanceOf(String.class)),
                                  satisfies(NETWORK_PEER_PORT, val -> val.isInstanceOf(Long.class)),
                                  satisfies(
                                      NETWORK_TYPE,
                                      AbstractDubboTest::assertUnknownServiceNetworkType))));
    } else {
      testing().waitAndAssertTraces(trace -> trace.hasSpansSatisfyingExactly(clientSpanAssert));
    }
  }

  /**
   * Tests unknown service span capture for the Triple protocol (gRPC over HTTP/2). Unlike the Dubbo
   * binary protocol, Triple transmits trace context as HTTP/2 headers, so the server _OTHER span is
   * always a child of the client span — resulting in 1 trace with 2 spans.
   */
  @Test
  void testTripleUnknownService() throws ReflectiveOperationException {
    // Triple protocol requires Dubbo 3.x
    Assumptions.assumeTrue(testLatestDeps(), "Triple protocol requires Dubbo 3.x");
    Assumptions.assumeTrue(canCaptureUnknownServiceSpans(), "Requires agent instrumentation");
    Assumptions.assumeTrue(emitPreviewRpcSemconv(), "Requires preview RPC semconv");

    int port = PortUtils.findOpenPort();

    ProtocolConfig tripleProtocol = new ProtocolConfig();
    tripleProtocol.setName("tri");
    tripleProtocol.setPort(port);

    DubboBootstrap bootstrap = DubboTestUtil.newDubboBootstrap();
    cleanup.deferCleanup(bootstrap::destroy);
    bootstrap
        .application(new ApplicationConfig("dubbo-test-triple-unknown-provider"))
        .service(configureServer())
        .protocol(tripleProtocol)
        .start();

    ReferenceConfig<MiddleService> unknownRef = new ReferenceConfig<>();
    unknownRef.setInterface(MiddleService.class);
    unknownRef.setGeneric("true");
    unknownRef.setRetries(0);
    unknownRef.setUrl("tri://localhost:" + port + "/?timeout=30000");

    ProtocolConfig consumerProtocolConfig = new ProtocolConfig();
    consumerProtocolConfig.setRegister(false);

    DubboBootstrap consumerBootstrap = DubboTestUtil.newDubboBootstrap();
    cleanup.deferCleanup(consumerBootstrap::destroy);
    consumerBootstrap
        .application(new ApplicationConfig("dubbo-test-triple-unknown-consumer"))
        .reference(unknownRef)
        .protocol(consumerProtocolConfig)
        .start();

    @SuppressWarnings({"rawtypes", "unchecked"})
    ReferenceConfig<GenericService> reference = (ReferenceConfig) unknownRef;
    GenericService genericService = reference.get();

    try {
      genericService.$invoke("hello", new String[] {String.class.getName()}, new Object[] {"test"});
    } catch (Throwable ignored) {
      // expected: MiddleService not exported on provider
    }

    // Triple uses HTTP/2 headers for trace context propagation, so the server _OTHER span
    // is always a child of the client span (1 trace), unlike the Dubbo binary protocol
    // which may produce 2 separate traces when attachments are unreachable.
    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("org.apache.dubbo.rpc.service.GenericService/$invoke")
                            .hasKind(SpanKind.CLIENT)
                            .hasNoParent()
                            .hasStatus(StatusData.error())
                            .hasAttributesSatisfyingExactly(
                                equalTo(RPC_SYSTEM, emitOldRpcSemconv() ? "apache_dubbo" : null),
                                equalTo(RPC_SYSTEM_NAME, "dubbo"),
                                equalTo(
                                    RPC_SERVICE,
                                    emitOldRpcSemconv()
                                        ? "org.apache.dubbo.rpc.service.GenericService"
                                        : null),
                                equalTo(
                                    RPC_METHOD,
                                    "org.apache.dubbo.rpc.service.GenericService/$invoke"),
                                satisfies(ERROR_TYPE, val -> val.isNotNull()),
                                equalTo(maybeStablePeerService(), testing().expectedPeerService()),
                                equalTo(SERVER_ADDRESS, "localhost"),
                                satisfies(SERVER_PORT, val -> val.isInstanceOf(Long.class)),
                                satisfies(
                                    NETWORK_PEER_ADDRESS, val -> val.isInstanceOf(String.class)),
                                satisfies(NETWORK_PEER_PORT, val -> val.isInstanceOf(Long.class)),
                                satisfies(NETWORK_TYPE, AbstractDubboTest::assertNetworkType)),
                    span ->
                        span.hasName("_OTHER")
                            .hasKind(SpanKind.SERVER)
                            .hasParent(trace.getSpan(0))
                            .hasStatus(StatusData.error())
                            .hasAttributesSatisfyingExactly(
                                equalTo(RPC_SYSTEM, emitOldRpcSemconv() ? "apache_dubbo" : null),
                                equalTo(RPC_SYSTEM_NAME, "dubbo"),
                                equalTo(RPC_METHOD, "_OTHER"),
                                equalTo(
                                    RPC_METHOD_ORIGINAL,
                                    "io.opentelemetry.instrumentation.apachedubbo.v2_7.api.MiddleService/$invoke"),
                                satisfies(ERROR_TYPE, val -> val.isNotNull()),
                                satisfies(
                                    NETWORK_PEER_ADDRESS, val -> val.isInstanceOf(String.class)),
                                satisfies(NETWORK_PEER_PORT, val -> val.isInstanceOf(Long.class)),
                                satisfies(
                                    NETWORK_TYPE,
                                    AbstractDubboTest::assertUnknownServiceNetworkType))));
  }

  static void assertErrorType(AbstractStringAssert<?> stringAssert) {
    if (emitPreviewRpcSemconv()) {
      stringAssert.isNotNull();
    } else {
      stringAssert.isNull();
    }
  }

  static void assertNetworkType(AbstractStringAssert<?> stringAssert) {
    assertLatestDeps(
        stringAssert,
        a ->
            a.satisfiesAnyOf(
                val -> assertThat(val).isEqualTo("ipv4"),
                val -> assertThat(val).isEqualTo("ipv6")));
  }

  static void assertUnknownServiceNetworkType(AbstractStringAssert<?> stringAssert) {
    stringAssert.satisfiesAnyOf(
        val -> assertThat(val).isEqualTo("ipv4"), val -> assertThat(val).isEqualTo("ipv6"));
  }

  static void assertLatestDeps(
      AbstractAssert<?, ?> assertion, Consumer<AbstractAssert<?, ?>> action) {
    if (testLatestDeps()) {
      action.accept(assertion);
    } else {
      assertion.isNull();
    }
  }
}
