/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v2_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static java.util.Arrays.asList;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

@SuppressWarnings("deprecation") // using deprecated semconv
class JedisAggregateTargetTest {

  private static final String MASTER_NAME = "mymaster";

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension
  private static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  private static GenericContainer<?> sentinelServer;
  private static String sentinelEndpoint;

  private static GenericContainer<?> clusterServer;
  private static Object cluster;
  private static String clusterTarget;
  private static String clusterHost;

  @BeforeAll
  static void setup() throws Exception {
    assumeTrue(classPresent("redis.clients.jedis.JedisSentinelPool"));
    assumeTrue(classPresent("redis.clients.jedis.JedisCluster"));

    startSentinelServer();
    startClusterServer();
  }

  @Test
  void sentinelDiscoveryAndCommandsUseConfiguredTarget() throws Exception {
    Class<?> poolClass = Class.forName("redis.clients.jedis.JedisSentinelPool");
    Object pool =
        poolClass
            .getConstructor(String.class, Set.class)
            .newInstance(MASTER_NAME, singleton(sentinelEndpoint));
    Object jedis = poolClass.getMethod("getResource").invoke(pool);
    try {
      jedis.getClass().getMethod("set", String.class, String.class).invoke(jedis, "key", "value");
    } finally {
      jedis.getClass().getMethod("close").invoke(jedis);
      poolClass.getMethod("destroy").invoke(pool);
    }

    await()
        .untilAsserted(
            () -> {
              assertThat(testing.spans())
                  .filteredOn(span -> span.getName().startsWith("SET"))
                  .anySatisfy(span -> assertTarget(span, sentinelEndpoint + "/" + MASTER_NAME));
              assertThat(testing.spans())
                  .filteredOn(
                      span ->
                          span.getName().startsWith("SENTINEL")
                              || span.getName().startsWith("SUBSCRIBE"))
                  .isNotEmpty()
                  .allSatisfy(span -> assertTarget(span, sentinelEndpoint + "/" + MASTER_NAME));
            });
  }

  @Test
  void clusterRefreshUsesConfiguredTarget() throws Exception {
    Field handlerField =
        Class.forName("redis.clients.jedis.BinaryJedisCluster")
            .getDeclaredField("connectionHandler");
    handlerField.setAccessible(true);
    Object handler = handlerField.get(cluster);

    Class<?> jedisClass = Class.forName("redis.clients.jedis.Jedis");
    Object unavailable =
        jedisClass.getConstructor(String.class, int.class).newInstance(clusterHost, 1);
    try {
      handler.getClass().getMethod("renewSlotCache", jedisClass).invoke(handler, unavailable);
    } finally {
      jedisClass.getMethod("close").invoke(unavailable);
    }

    await()
        .untilAsserted(
            () ->
                assertThat(testing.spans())
                    .filteredOn(span -> span.getName().startsWith("CLUSTER"))
                    .isNotEmpty()
                    .allSatisfy(span -> assertTarget(span, clusterTarget)));
  }

  private static void startSentinelServer() throws Exception {
    int masterPort = availablePort();
    int sentinelPort = availablePort();
    String sentinelConfig =
        "port "
            + sentinelPort
            + "\\nsentinel monitor "
            + MASTER_NAME
            + " 127.0.0.1 "
            + masterPort
            + " 1\\n";
    sentinelServer =
        new GenericContainer<>("redis:6.2.3-alpine")
            .withExposedPorts(masterPort, sentinelPort)
            .withCommand(
                "sh",
                "-c",
                "redis-server --port "
                    + masterPort
                    + " --daemonize yes && printf '"
                    + sentinelConfig
                    + "' > /tmp/sentinel.conf && exec redis-server /tmp/sentinel.conf --sentinel")
            .waitingFor(Wait.forListeningPorts(masterPort, sentinelPort));
    sentinelServer.setPortBindings(
        asList(masterPort + ":" + masterPort, sentinelPort + ":" + sentinelPort));
    sentinelServer.start();
    cleanup.deferAfterAll(sentinelServer::stop);
    sentinelEndpoint = sentinelServer.getHost() + ":" + sentinelPort;
  }

  private static void startClusterServer() throws Exception {
    int clusterPort = availablePort();
    clusterServer = new GenericContainer<>("redis:6.2.3-alpine").withExposedPorts(6379);
    clusterServer.setPortBindings(singletonList(clusterPort + ":6379"));
    clusterServer.withCommand(
        "redis-server",
        "--cluster-enabled",
        "yes",
        "--cluster-config-file",
        "/tmp/nodes.conf",
        "--cluster-announce-ip",
        "127.0.0.1",
        "--cluster-announce-port",
        Integer.toString(clusterPort));
    clusterServer.start();
    cleanup.deferAfterAll(clusterServer::stop);

    Container.ExecResult result =
        clusterServer.execInContainer(
            "sh", "-c", "redis-cli cluster addslots $(seq 0 16383) >/dev/null");
    if (result.getExitCode() != 0) {
      throw new IllegalStateException(result.getStderr());
    }

    clusterHost = clusterServer.getHost();
    Class<?> hostAndPortClass = Class.forName("redis.clients.jedis.HostAndPort");
    Object selected =
        hostAndPortClass
            .getConstructor(String.class, int.class)
            .newInstance(clusterHost, clusterPort);
    Object unavailable =
        hostAndPortClass.getConstructor(String.class, int.class).newInstance(clusterHost, 1);
    Set<Object> nodes = new LinkedHashSet<>(asList(selected, unavailable));
    clusterTarget = clusterHost + ":1," + clusterHost + ":" + clusterPort;

    cluster =
        Class.forName("redis.clients.jedis.JedisCluster")
            .getConstructor(Set.class)
            .newInstance(nodes);
    cleanup.deferAfterAll(() -> cluster.getClass().getMethod("close").invoke(cluster));
  }

  private static void assertTarget(SpanData span, String configuredTarget) {
    if (emitStableDatabaseSemconv()) {
      assertThat(span.getAttributes().get(SERVER_ADDRESS)).isEqualTo(configuredTarget);
      assertThat(span.getAttributes().get(SERVER_PORT)).isNull();
    } else {
      assertThat(span.getAttributes().get(SERVER_ADDRESS)).isNotEqualTo(configuredTarget);
      assertThat(span.getAttributes().get(SERVER_PORT)).isNotNull();
    }
  }

  private static int availablePort() throws Exception {
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    }
  }

  private static boolean classPresent(String className) {
    try {
      Class.forName(className);
      return true;
    } catch (ClassNotFoundException ignored) {
      return false;
    }
  }
}
