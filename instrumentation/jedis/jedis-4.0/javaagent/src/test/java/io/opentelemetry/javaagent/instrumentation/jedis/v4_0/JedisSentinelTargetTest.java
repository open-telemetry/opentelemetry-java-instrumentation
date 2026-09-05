/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v4_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static java.util.Arrays.asList;
import static java.util.Collections.singleton;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.net.ServerSocket;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

@SuppressWarnings("deprecation") // using deprecated semconv
class JedisSentinelTargetTest {

  private static final String MASTER_NAME = "mymaster";

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension
  private static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  private static GenericContainer<?> sentinelServer;
  private static String sentinelEndpoint;
  private static int sentinelPort;
  private static int replicaPort;

  @BeforeAll
  static void setup() throws Exception {
    assumeTrue(classPresent("redis.clients.jedis.JedisSentinelPool"));

    int masterPort = availablePort();
    replicaPort = availablePort();
    sentinelPort = availablePort();
    String sentinelConfig =
        "port "
            + sentinelPort
            + "\\nsentinel monitor "
            + MASTER_NAME
            + " 127.0.0.1 "
            + masterPort
            + " 1\\nsentinel down-after-milliseconds "
            + MASTER_NAME
            + " 500\\nsentinel failover-timeout "
            + MASTER_NAME
            + " 5000\\n";
    sentinelServer =
        new GenericContainer<>("redis:6.2.3-alpine")
            .withExposedPorts(masterPort, replicaPort, sentinelPort)
            .withCommand(
                "sh",
                "-c",
                "redis-server --port "
                    + masterPort
                    + " --daemonize yes && redis-server --port "
                    + replicaPort
                    + " --replicaof 127.0.0.1 "
                    + masterPort
                    + " --daemonize yes && printf '"
                    + sentinelConfig
                    + "' > /tmp/sentinel.conf && exec redis-server /tmp/sentinel.conf --sentinel")
            .waitingFor(Wait.forListeningPorts(masterPort, replicaPort, sentinelPort));
    sentinelServer.setPortBindings(
        asList(
            masterPort + ":" + masterPort,
            replicaPort + ":" + replicaPort,
            sentinelPort + ":" + sentinelPort));
    sentinelServer.start();
    cleanup.deferAfterAll(sentinelServer::stop);
    sentinelEndpoint = sentinelServer.getHost() + ":" + sentinelPort;
  }

  @Test
  void configuredTargetSurvivesMasterFailover() throws Exception {
    Class<?> poolClass = Class.forName("redis.clients.jedis.JedisSentinelPool");
    Object pool =
        poolClass
            .getConstructor(String.class, Set.class)
            .newInstance(MASTER_NAME, singleton(sentinelEndpoint));
    try {
      set(poolClass, pool, "before-failover");
      await()
          .untilAsserted(
              () ->
                  assertThat(testing.spans())
                      .filteredOn(span -> span.getName().startsWith("SET"))
                      .anySatisfy(
                          span -> assertTarget(span, sentinelEndpoint + "/" + MASTER_NAME)));
      testing.clearData();

      Container.ExecResult failover =
          sentinelServer.execInContainer(
              "redis-cli",
              "-p",
              Integer.toString(sentinelPort),
              "SENTINEL",
              "failover",
              MASTER_NAME);
      if (failover.getExitCode() != 0) {
        throw new IllegalStateException(failover.getStderr());
      }
      Container.ExecResult promoted =
          sentinelServer.execInContainer(
              "sh",
              "-c",
              "for i in $(seq 1 100); do port=$(redis-cli -p "
                  + sentinelPort
                  + " SENTINEL get-master-addr-by-name "
                  + MASTER_NAME
                  + " | tail -n 1); [ \"$port\" = \""
                  + replicaPort
                  + "\" ] && exit 0; sleep 0.1; done; exit 1");
      if (promoted.getExitCode() != 0) {
        throw new IllegalStateException("Sentinel did not promote the replica");
      }

      await().untilAsserted(() -> set(poolClass, pool, "after-failover"));
    } finally {
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

  private static void set(Class<?> poolClass, Object pool, String key) throws Exception {
    Object jedis = poolClass.getMethod("getResource").invoke(pool);
    try {
      jedis.getClass().getMethod("set", String.class, String.class).invoke(jedis, key, "value");
    } finally {
      jedis.getClass().getMethod("close").invoke(jedis);
    }
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
