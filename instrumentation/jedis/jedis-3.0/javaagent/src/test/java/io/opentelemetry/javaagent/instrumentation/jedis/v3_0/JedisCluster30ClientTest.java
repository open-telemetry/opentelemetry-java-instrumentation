/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v3_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.lang.reflect.Field;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import redis.clients.jedis.BinaryJedisCluster;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.JedisClusterConnectionHandler;
import redis.clients.jedis.JedisPool;

class JedisCluster30ClientTest {

  private static final String CLUSTER_NODE_HOST = "127.0.0.1";

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension
  private static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  private static GenericContainer<?> clusterServer;
  private static JedisCluster cluster;
  private static String clusterTarget;
  private static String clusterHost;
  private static int clusterPort;

  @BeforeAll
  static void setup() throws Exception {
    startClusterServer();
  }

  @Test
  void clusterRefreshAndCommandsUseConfiguredTarget() throws ReflectiveOperationException {
    assertThat(cluster.set("key", "value")).isEqualTo("OK");

    Field handlerField = BinaryJedisCluster.class.getDeclaredField("connectionHandler");
    handlerField.setAccessible(true);
    JedisClusterConnectionHandler handler =
        (JedisClusterConnectionHandler) handlerField.get(cluster);

    try (Jedis unavailable = new Jedis(clusterHost, 1)) {
      handler.renewSlotCache(unavailable);
    }

    await()
        .untilAsserted(
            () -> {
              assertThat(testing.spans())
                  .filteredOn(span -> span.getName().startsWith("SET"))
                  .singleElement()
                  .satisfies(
                      span -> {
                        assertThat(span.getName())
                            .isEqualTo(
                                emitStableDatabaseSemconv() ? "SET " + clusterTarget : "SET");
                        assertThat(span.getAttributes().get(SERVER_ADDRESS))
                            .isEqualTo(
                                emitStableDatabaseSemconv() ? clusterTarget : CLUSTER_NODE_HOST);
                        assertThat(span.getAttributes().get(SERVER_PORT))
                            .isEqualTo(emitStableDatabaseSemconv() ? null : (long) clusterPort);
                      });
              assertThat(testing.spans())
                  .filteredOn(span -> span.getName().startsWith("CLUSTER"))
                  .isNotEmpty()
                  .allSatisfy(
                      span -> {
                        if (emitStableDatabaseSemconv()) {
                          assertThat(span.getAttributes().get(SERVER_ADDRESS))
                              .isEqualTo(clusterTarget);
                          assertThat(span.getAttributes().get(SERVER_PORT)).isNull();
                        } else {
                          assertThat(span.getAttributes().get(SERVER_ADDRESS))
                              .isNotEqualTo(clusterTarget);
                          assertThat(span.getAttributes().get(SERVER_PORT)).isNotNull();
                        }
                      });
            });
  }

  @Test
  void clusterNodePoolsUseConfiguredTarget() {
    Map<String, JedisPool> clusterNodes = cluster.getClusterNodes();
    JedisPool pool = clusterNodes.get(CLUSTER_NODE_HOST + ":" + clusterPort);
    assertThat(pool).isNotNull();

    try (Jedis jedis = pool.getResource()) {
      assertThat(jedis.set("pool-key", "value")).isEqualTo("OK");
    }

    await()
        .untilAsserted(
            () ->
                assertThat(testing.spans())
                    .filteredOn(span -> span.getName().startsWith("SET"))
                    .singleElement()
                    .satisfies(
                        span -> {
                          assertThat(span.getName())
                              .isEqualTo(
                                  emitStableDatabaseSemconv() ? "SET " + clusterTarget : "SET");
                          assertThat(span.getAttributes().get(SERVER_ADDRESS))
                              .isEqualTo(
                                  emitStableDatabaseSemconv() ? clusterTarget : CLUSTER_NODE_HOST);
                          assertThat(span.getAttributes().get(SERVER_PORT))
                              .isEqualTo(emitStableDatabaseSemconv() ? null : (long) clusterPort);
                        }));
  }

  private static void startClusterServer() throws Exception {
    clusterPort = PortUtils.findOpenPort();
    clusterServer = new GenericContainer<>("redis:6.2.3-alpine").withExposedPorts(6379);
    clusterServer.setPortBindings(singletonList(clusterPort + ":6379"));
    clusterServer.withCommand(
        "redis-server",
        "--cluster-enabled",
        "yes",
        "--cluster-config-file",
        "/tmp/nodes.conf",
        "--cluster-announce-ip",
        CLUSTER_NODE_HOST,
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
    await()
        .untilAsserted(
            () -> {
              Container.ExecResult clusterInfo =
                  clusterServer.execInContainer("redis-cli", "cluster", "info");
              assertThat(clusterInfo.getExitCode()).isZero();
              assertThat(clusterInfo.getStdout()).contains("cluster_state:ok");
            });

    clusterHost = clusterServer.getHost();
    HostAndPort selected = new HostAndPort(clusterHost, clusterPort);
    HostAndPort unavailable = new HostAndPort(clusterHost, 1);
    Set<HostAndPort> nodes = new LinkedHashSet<>(asList(selected, unavailable));
    clusterTarget = clusterHost + ":1," + clusterHost + ":" + clusterPort;

    cluster = new JedisCluster(nodes);
    cleanup.deferAfterAll(cluster);
  }
}
