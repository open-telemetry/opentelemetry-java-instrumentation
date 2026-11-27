/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.nacosclient.v2_0;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PROTOCOL_NAME;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_TRANSPORT;
import static io.opentelemetry.semconv.RpcAttributes.RPC_METHOD;
import static io.opentelemetry.semconv.RpcAttributes.RPC_SYSTEM_NAME;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static java.util.Arrays.asList;
import static org.awaitility.Awaitility.await;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.AbstractListener;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.common.remote.client.RpcClient;
import com.alibaba.nacos.common.remote.client.RpcClientFactory;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

class NacosClientServerTest {

  private static final int HTTP_PORT = 8848;
  private static final int GRPC_PORT = 9848;
  // the client connects to the gRPC port that it derives by adding this offset to the server port
  private static final int GRPC_PORT_OFFSET = 1000;

  private static final String GROUP = "DEFAULT_GROUP";
  private static final long TIMEOUT_MILLIS = 5000;

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  private static final GenericContainer<?> nacos =
      new GenericContainer<>("nacos/nacos-server:v2.5.2")
          .withEnv("MODE", "standalone")
          .withEnv("JVM_XMS", "256m")
          .withEnv("JVM_XMX", "256m")
          .withEnv("JVM_XMN", "128m")
          .withExposedPorts(HTTP_PORT, GRPC_PORT)
          .waitingFor(Wait.forHttp("/nacos/v1/console/health/readiness").forPort(HTTP_PORT))
          .withStartupTimeout(Duration.ofMinutes(2));

  private static String host;
  private static int grpcPort;

  @BeforeAll
  static void setUp() {
    nacos.start();
    host = nacos.getHost();
    grpcPort = nacos.getMappedPort(GRPC_PORT);
  }

  @AfterAll
  static void tearDown() {
    nacos.stop();
  }

  @Test
  void connectionCheck() throws NacosException {
    createNamingService();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("connection/serverCheck")
                        .hasKind(SpanKind.CLIENT)
                        .hasStatus(StatusData.unset())
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            attributes("connection", "serverCheck", "ServerCheckRequest"))));
  }

  @Test
  void registerInstance() throws NacosException {
    NamingService namingService = createNamingService();
    testing.clearData();

    testing.runWithSpan(
        "parent", () -> namingService.registerInstance("com.example.Service", "127.0.0.1", 8080));

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName("naming/registerInstance")
                        .hasKind(SpanKind.CLIENT)
                        .hasStatus(StatusData.unset())
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            attributes(
                                "naming",
                                "registerInstance",
                                "InstanceRequest",
                                equalTo(stringKey("nacos.namespace"), "public"),
                                equalTo(stringKey("nacos.group"), GROUP),
                                equalTo(stringKey("nacos.service.name"), "com.example.Service")))));
  }

  @Test
  void publishAndQueryConfig() throws NacosException {
    ConfigService configService = createConfigService();
    // connect to the server and wait until the published config can be read back
    assertThat(configService.publishConfig("app.yaml", GROUP, "content")).isTrue();
    await()
        .untilAsserted(
            () ->
                assertThat(configService.getConfig("app.yaml", GROUP, TIMEOUT_MILLIS))
                    .isEqualTo("content"));
    testing.clearData();

    testing.runWithSpan(
        "parent",
        () -> {
          assertThat(configService.publishConfig("app.yaml", GROUP, "content")).isTrue();
          assertThat(configService.getConfig("app.yaml", GROUP, TIMEOUT_MILLIS))
              .isEqualTo("content");
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName("config/publishConfig")
                        .hasKind(SpanKind.CLIENT)
                        .hasStatus(StatusData.unset())
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            attributes(
                                "config",
                                "publishConfig",
                                "ConfigPublishRequest",
                                equalTo(stringKey("nacos.data.id"), "app.yaml"),
                                equalTo(stringKey("nacos.group"), GROUP))),
                span ->
                    span.hasName("config/queryConfig")
                        .hasKind(SpanKind.CLIENT)
                        .hasStatus(StatusData.unset())
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            attributes(
                                "config",
                                "queryConfig",
                                "ConfigQueryRequest",
                                equalTo(stringKey("nacos.data.id"), "app.yaml"),
                                equalTo(stringKey("nacos.group"), GROUP)))));
  }

  @Test
  void serviceChangeNotification() throws NacosException {
    NamingService namingService = createNamingService();
    namingService.subscribe("com.example.NotifiedService", event -> {});
    namingService.registerInstance("com.example.NotifiedService", "127.0.0.1", 8080);

    // the client also sends requests from its background threads, look only for the pushed request
    await()
        .untilAsserted(
            () ->
                assertThat(testing.spans())
                    .anySatisfy(
                        span ->
                            assertThat(span)
                                .hasName("naming/notifySubscribeChange")
                                .hasKind(SpanKind.SERVER)
                                .hasStatus(StatusData.unset())
                                .hasNoParent()
                                .hasAttributesSatisfyingExactly(
                                    attributes(
                                        "naming",
                                        "notifySubscribeChange",
                                        "NotifySubscriberRequest",
                                        equalTo(stringKey("nacos.group"), GROUP),
                                        equalTo(
                                            stringKey("nacos.service.name"),
                                            "com.example.NotifiedService")))));
  }

  @Test
  void configChangeNotification() throws NacosException {
    ConfigService configService = createConfigService();
    configService.addListener(
        "notified.yaml",
        GROUP,
        new AbstractListener() {
          @Override
          public void receiveConfigInfo(String configInfo) {}
        });
    assertThat(configService.publishConfig("notified.yaml", GROUP, "content")).isTrue();

    // the client also sends requests from its background threads, look only for the pushed request
    await()
        .untilAsserted(
            () ->
                assertThat(testing.spans())
                    .anySatisfy(
                        span ->
                            assertThat(span)
                                .hasName("config/notifyConfigChange")
                                .hasKind(SpanKind.SERVER)
                                .hasStatus(StatusData.unset())
                                .hasNoParent()
                                .hasAttributesSatisfyingExactly(
                                    attributes(
                                        "config",
                                        "notifyConfigChange",
                                        "ConfigChangeNotifyRequest",
                                        equalTo(stringKey("nacos.data.id"), "notified.yaml"),
                                        equalTo(stringKey("nacos.group"), GROUP)))));
  }

  private static NamingService createNamingService() throws NacosException {
    NamingService namingService = NacosFactory.createNamingService(serverAddress());
    cleanup.deferCleanup(namingService::shutDown);
    return namingService;
  }

  private static ConfigService createConfigService() throws NacosException {
    ConfigService configService = NacosFactory.createConfigService(serverAddress());
    cleanup.deferCleanup(
        () -> {
          configService.shutDown();
          // early 2.x versions leave the RPC clients of the config service running
          for (Map.Entry<String, RpcClient> entry :
              new ArrayList<>(RpcClientFactory.getAllClientEntries())) {
            RpcClientFactory.destroyClient(entry.getKey());
          }
        });
    return configService;
  }

  private static String serverAddress() {
    // Only the gRPC port is used by these tests. The client can't be configured with it directly,
    // so pass a server port that makes the client derive the mapped gRPC port.
    return host + ":" + (grpcPort - GRPC_PORT_OFFSET);
  }

  private static List<AttributeAssertion> attributes(
      String category, String operation, String requestType, AttributeAssertion... assertions) {
    List<AttributeAssertion> result =
        new ArrayList<>(
            asList(
                equalTo(RPC_SYSTEM_NAME, "nacos"),
                equalTo(RPC_METHOD, category + "/" + operation),
                equalTo(SERVER_ADDRESS, host),
                equalTo(SERVER_PORT, grpcPort),
                equalTo(NETWORK_TRANSPORT, "tcp"),
                equalTo(NETWORK_PROTOCOL_NAME, "grpc"),
                equalTo(stringKey("nacos.category"), category),
                equalTo(stringKey("nacos.request.type"), requestType)));
    result.addAll(asList(assertions));
    return result;
  }
}
