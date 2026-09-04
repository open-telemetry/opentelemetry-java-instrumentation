/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v4_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldDatabaseSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStable;
import static io.opentelemetry.instrumentation.testing.util.TestLatestDeps.testLatestDeps;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.DbAttributes.DB_NAMESPACE;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_TYPE;
import static io.opentelemetry.semconv.NetworkAttributes.NetworkTypeValues.IPV4;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_STATEMENT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues.REDIS;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.TraceAssert;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import redis.clients.jedis.Connection;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisClientConfig;

@SuppressWarnings("deprecation") // using deprecated semconv
class JedisConnectionProviderTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension
  private static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  private static final GenericContainer<?> redisServer =
      new GenericContainer<>("redis:6.2.3-alpine").withExposedPorts(6379);

  private static Jedis jedis;
  private static AutoCloseable provider;
  private static boolean connectionSendsHello;
  private static Set<HostAndPort> configuredNodes;
  private static String configuredTarget;
  private static String host;
  private static String ip;
  private static int port;

  @BeforeAll
  static void setup() throws IOException, InterruptedException, ReflectiveOperationException {
    try (ServerSocket socket = new ServerSocket(0)) {
      port = socket.getLocalPort();
    }
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

    Class<? extends AutoCloseable> providerClass = providerClass();
    provider =
        providerClass
            .getConstructor(Set.class, JedisClientConfig.class)
            .newInstance(configuredNodes, clientConfig());
    cleanup.deferAfterAll(provider);

    Connection connection = getConnection(selectedNode);
    jedis = new Jedis(connection);
    cleanup.deferAfterAll(jedis);
  }

  @Test
  void internalHealthCheckUsesConfiguredClusterNodesAsServerTarget()
      throws ReflectiveOperationException {
    try (Connection ignored = getConnection()) {
      if (connectionSendsHello) {
        testing.waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName(
                            emitStableDatabaseSemconv() ? "HELLO " + configuredTarget : "HELLO")),
            JedisConnectionProviderTest::assertPingTrace);
      } else {
        testing.waitAndAssertTraces(JedisConnectionProviderTest::assertPingTrace);
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
                    span.hasName(emitStableDatabaseSemconv() ? "PING " + configuredTarget : "PING")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), REDIS),
                            equalTo(maybeStable(DB_STATEMENT), "PING"),
                            equalTo(maybeStable(DB_OPERATION), "PING"),
                            equalTo(DB_NAMESPACE, emitStableDatabaseSemconv() ? "0" : null),
                            equalTo(
                                SERVER_ADDRESS,
                                emitStableDatabaseSemconv() ? configuredTarget : host),
                            equalTo(SERVER_PORT, emitStableDatabaseSemconv() ? null : (long) port),
                            equalTo(NETWORK_TYPE, emitOldDatabaseSemconv() ? IPV4 : null),
                            equalTo(NETWORK_PEER_PORT, port),
                            equalTo(NETWORK_PEER_ADDRESS, ip))));
  }

  @Test
  void periodicTopologyRefreshUsesConfiguredClusterNodesAsServerTarget()
      throws ReflectiveOperationException {
    assumeTrue(testLatestDeps());

    AutoCloseable refreshingProvider =
        providerClass()
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

  private static Connection getConnection(Object... arguments) throws ReflectiveOperationException {
    Class<?>[] parameterTypes =
        arguments.length == 0 ? new Class<?>[0] : new Class<?>[] {HostAndPort.class};
    return (Connection)
        provider.getClass().getMethod("getConnection", parameterTypes).invoke(provider, arguments);
  }

  private static void assertPingTrace(TraceAssert trace) {
    trace.hasSpansSatisfyingExactly(
        span ->
            span.hasName(emitStableDatabaseSemconv() ? "PING " + configuredTarget : "PING")
                .hasKind(SpanKind.CLIENT)
                .hasAttributesSatisfyingExactly(
                    equalTo(maybeStable(DB_SYSTEM), REDIS),
                    equalTo(maybeStable(DB_STATEMENT), "PING"),
                    equalTo(maybeStable(DB_OPERATION), "PING"),
                    equalTo(DB_NAMESPACE, emitStableDatabaseSemconv() ? "0" : null),
                    satisfies(
                        SERVER_ADDRESS,
                        val -> {
                          if (emitStableDatabaseSemconv()) {
                            val.isEqualTo(configuredTarget);
                          } else {
                            val.isIn(host, ip);
                          }
                        }),
                    equalTo(SERVER_PORT, emitStableDatabaseSemconv() ? null : (long) port),
                    equalTo(NETWORK_TYPE, emitOldDatabaseSemconv() ? IPV4 : null),
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

  private static Class<? extends AutoCloseable> providerClass() throws ClassNotFoundException {
    try {
      return Class.forName("redis.clients.jedis.providers.JedisClusterConnectionProvider")
          .asSubclass(AutoCloseable.class);
    } catch (ClassNotFoundException ignored) {
      return Class.forName("redis.clients.jedis.providers.ClusterConnectionProvider")
          .asSubclass(AutoCloseable.class);
    }
  }
}
