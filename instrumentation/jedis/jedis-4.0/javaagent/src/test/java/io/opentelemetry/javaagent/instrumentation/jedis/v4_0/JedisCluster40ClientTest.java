/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v4_0;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.DbAttributes.DB_NAMESPACE;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_NAME;
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_TEXT;
import static io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues.REDIS;
import static java.util.Arrays.asList;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.TraceAssert;
import java.io.IOException;
import java.net.InetAddress;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import redis.clients.jedis.Connection;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.providers.ClusterConnectionProvider;
import redis.clients.jedis.util.JedisClusterCRC16;

@SuppressWarnings("deprecation") // using deprecated semconv
class JedisCluster40ClientTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension
  private static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  private static final GenericContainer<?> redisServer =
      new GenericContainer<>("redis:6.2.3-alpine").withExposedPorts(6379);

  private static Jedis jedis;
  private static ClusterConnectionProvider provider;
  private static boolean connectionSendsHello;
  private static Set<HostAndPort> configuredNodes;
  private static String configuredTarget;
  private static String host;
  private static String ip;
  private static int port;

  @BeforeAll
  static void setup() throws IOException, InterruptedException, ReflectiveOperationException {
    port = PortUtils.findOpenPort();
    redisServer.setPortBindings(singletonList(port + ":6379"));
    redisServer.withCommand(
        "redis-server",
        "--cluster-enabled",
        "yes",
        "--cluster-config-file",
        "/tmp/nodes.conf",
        "--cluster-announce-ip",
        "127.0.0.1",
        "--cluster-announce-port",
        Integer.toString(port));
    redisServer.start();
    cleanup.deferAfterAll(redisServer::stop);

    host = redisServer.getHost();
    ip = InetAddress.getByName(host).getHostAddress();
    Container.ExecResult result =
        redisServer.execInContainer(
            "sh", "-c", "redis-cli cluster addslots $(seq 0 16383) >/dev/null");
    if (result.getExitCode() != 0) {
      throw new IllegalStateException(result.getStderr());
    }

    HostAndPort selectedNode = new HostAndPort(host, port);
    HostAndPort unavailableNode = new HostAndPort(host, 1);
    configuredNodes = new LinkedHashSet<>(asList(selectedNode, unavailableNode));
    configuredTarget = host + ":1," + host + ":" + port;

    provider = new ClusterConnectionProvider(configuredNodes, clientConfig());
    cleanup.deferAfterAll(provider);

    Connection connection = getConnection(selectedNode);
    jedis = new Jedis(connection);
    cleanup.deferAfterAll(jedis);
  }

  @Test
  void internalHealthCheckUsesConfiguredClusterNodesAsServerTarget() {
    try (Connection ignored = getConnection()) {
      if (connectionSendsHello) {
        testing.waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(span -> span.hasName("HELLO " + configuredTarget)),
            JedisCluster40ClientTest::assertPingTrace);
      } else {
        testing.waitAndAssertTraces(JedisCluster40ClientTest::assertPingTrace);
      }
    }
  }

  @Test
  void commandUsesConfiguredClusterNodesAsServerTarget() {
    jedis.ping();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("PING " + configuredTarget)
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM_NAME, REDIS),
                            equalTo(DB_QUERY_TEXT, "PING"),
                            equalTo(DB_OPERATION_NAME, "PING"),
                            equalTo(DB_NAMESPACE, "0"),
                            equalTo(SERVER_ADDRESS, configuredTarget),
                            equalTo(NETWORK_PEER_PORT, port),
                            equalTo(NETWORK_PEER_ADDRESS, ip))));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void redirectedCommandUsesTerminalReply(boolean fail) throws ReflectiveOperationException {
    await().untilAsserted(() -> assertThat(jedis.clusterInfo()).contains("cluster_state:ok"));
    JedisCluster cluster = new JedisCluster(singleton(new HostAndPort(host, port)), clientConfig());
    cleanup.deferCleanup(cluster);
    String key = fail ? "redirect-error" : "redirect-success";
    jedis.del(key);
    assertThat(cluster.get(key)).isNull();
    String script =
        "if redis.call('EXISTS', KEYS[1]) == 0 then "
            + "redis.call('SET', KEYS[1], 'attempted'); "
            + "return redis.error_reply('MOVED "
            + JedisClusterCRC16.getSlot(key)
            + " 127.0.0.1:"
            + port
            + "') end "
            + (fail
                ? "return redis.error_reply('WRONGTYPE terminal failure')"
                : "return 'recovered'");
    testing.clearData();

    if (fail) {
      assertThatThrownBy(() -> cluster.eval(script, singletonList(key), singletonList("unused")))
          .hasMessageStartingWith("WRONGTYPE");
    } else {
      assertThat(cluster.eval(script, singletonList(key), singletonList("unused")))
          .isEqualTo("recovered");
    }

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("EVAL " + host + ":" + port)
                        .satisfies(
                            spanData ->
                                assertThat(spanData.getAttributes().get(ERROR_TYPE))
                                    .isEqualTo(fail ? "WRONGTYPE" : null))),
        trace ->
            trace.hasSpansSatisfyingExactly(span -> span.hasName("CLUSTER " + host + ":" + port)),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("EVAL " + host + ":" + port)
                        .satisfies(
                            spanData ->
                                assertThat(spanData.getAttributes().get(ERROR_TYPE)).isNull())));
    testing.waitAndAssertMetrics(
        "io.opentelemetry.jedis-4.0",
        "db.client.operation.duration",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric.getHistogramData().getPoints())
                        .filteredOn(
                            point -> "EVAL".equals(point.getAttributes().get(DB_OPERATION_NAME)))
                        .anySatisfy(
                            point ->
                                assertThat(point.getAttributes().get(ERROR_TYPE))
                                    .isEqualTo(fail ? "WRONGTYPE" : null))));

    testing.clearData();
    assertThat(cluster.get(key)).isEqualTo("attempted");
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("GET " + host + ":" + port)
                        .satisfies(
                            spanData ->
                                assertThat(spanData.getAttributes().get(ERROR_TYPE)).isNull())));
    testing.waitAndAssertMetrics(
        "io.opentelemetry.jedis-4.0",
        "db.client.operation.duration",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric.getHistogramData().getPoints())
                        .allSatisfy(
                            point -> assertThat(point.getAttributes().get(ERROR_TYPE)).isNull())));
  }

  @Test
  void periodicTopologyRefreshUsesConfiguredClusterNodesAsServerTarget()
      throws ReflectiveOperationException {
    assumeTrue(Boolean.getBoolean("testJedis51OrLater"));

    AutoCloseable refreshingProvider =
        ClusterConnectionProvider.class
            .getConstructor(
                Set.class, JedisClientConfig.class, GenericObjectPoolConfig.class, Duration.class)
            .newInstance(
                configuredNodes,
                clientConfig(),
                new GenericObjectPoolConfig<Connection>(),
                Duration.ofMillis(100));
    cleanup.deferCleanup(refreshingProvider);
    testing.clearData();

    await()
        .untilAsserted(
            () ->
                assertThat(testing.spans())
                    .filteredOn(span -> span.getName().startsWith("CLUSTER"))
                    .isNotEmpty()
                    .allSatisfy(
                        span -> {
                          assertThat(span.getAttributes().get(SERVER_ADDRESS))
                              .isEqualTo(configuredTarget);
                          assertThat(span.getAttributes().get(SERVER_PORT)).isNull();
                        }));
  }

  private static Connection getConnection() {
    return provider.getConnection();
  }

  private static Connection getConnection(HostAndPort hostAndPort) {
    return provider.getConnection(hostAndPort);
  }

  private static void assertPingTrace(TraceAssert trace) {
    trace.hasSpansSatisfyingExactly(
        span ->
            span.hasName("PING " + configuredTarget)
                .hasKind(SpanKind.CLIENT)
                .hasAttributesSatisfyingExactly(
                    equalTo(DB_SYSTEM_NAME, REDIS),
                    equalTo(DB_QUERY_TEXT, "PING"),
                    equalTo(DB_OPERATION_NAME, "PING"),
                    equalTo(DB_NAMESPACE, "0"),
                    satisfies(
                        SERVER_ADDRESS,
                        val -> {
                          val.isEqualTo(configuredTarget);
                        }),
                    equalTo(NETWORK_PEER_PORT, port),
                    equalTo(NETWORK_PEER_ADDRESS, ip)));
  }

  private static JedisClientConfig clientConfig() throws ReflectiveOperationException {
    DefaultJedisClientConfig.Builder builder = DefaultJedisClientConfig.builder();
    try {
      builder.getClass().getMethod("resp2").invoke(builder);
      builder.getClass().getMethod("autoNegotiateProtocol", boolean.class).invoke(builder, false);
      connectionSendsHello = true;
    } catch (NoSuchMethodException ignored) {
      // RESP2 is the default in older Jedis versions.
    }

    try {
      Class<?> configClass = Class.forName("redis.clients.jedis.ClientSetInfoConfig");
      Object disabled = configClass.getField("DISABLED").get(null);
      builder.getClass().getMethod("clientSetInfoConfig", configClass).invoke(builder, disabled);
    } catch (ClassNotFoundException ignored) {
      // Older Jedis versions do not send client metadata.
    }
    return builder.build();
  }
}
