/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v2_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static java.nio.charset.StandardCharsets.US_ASCII;
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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.net.Socket;
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
  private static Object clusterHandler;
  private static Object clusterNode;
  private static String clusterTarget;
  private static String clusterHost;
  private static int clusterPort;

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

  @Test
  void clusterRerouteUsesLastPeer() throws Exception {
    String key = "rerouted";
    int slot =
        (int)
            Class.forName("redis.clients.util.JedisClusterCRC16")
                .getMethod("getSlot", String.class)
                .invoke(null, key);
    Class<?> hostAndPortClass = Class.forName("redis.clients.jedis.HostAndPort");

    try (AskingServer askingServer = new AskingServer(slot, clusterPort)) {
      Object askingNode =
          hostAndPortClass
              .getConstructor(String.class, int.class)
              .newInstance("127.0.0.1", askingServer.getPort());
      assignSlotToNode(clusterHandler, slot, askingNode);
      try {
        cluster
            .getClass()
            .getMethod("set", String.class, String.class)
            .invoke(cluster, key, "value");
        askingServer.awaitRequest();
      } finally {
        assignSlotToNode(clusterHandler, slot, clusterNode);
      }
    }

    await()
        .untilAsserted(
            () ->
                assertThat(testing.spans())
                    .filteredOn(span -> span.getName().startsWith("SET"))
                    .singleElement()
                    .satisfies(
                        span -> {
                          assertTarget(span, clusterTarget);
                          if (emitStableDatabaseSemconv()) {
                            assertThat(span.getAttributes().get(NETWORK_PEER_ADDRESS))
                                .isEqualTo("127.0.0.1");
                            assertThat(span.getAttributes().get(NETWORK_PEER_PORT))
                                .isEqualTo((long) clusterPort);
                          } else {
                            assertThat(span.getAttributes().get(NETWORK_PEER_ADDRESS)).isNull();
                            assertThat(span.getAttributes().get(NETWORK_PEER_PORT)).isNull();
                          }
                        }));
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
    clusterPort = availablePort();
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
    await()
        .untilAsserted(
            () ->
                assertThat(
                        clusterServer.execInContainer("redis-cli", "cluster", "info").getStdout())
                    .contains("cluster_state:ok"));

    clusterHost = clusterServer.getHost();
    Class<?> hostAndPortClass = Class.forName("redis.clients.jedis.HostAndPort");
    clusterNode =
        hostAndPortClass
            .getConstructor(String.class, int.class)
            .newInstance(clusterHost, clusterPort);
    Object unavailable =
        hostAndPortClass.getConstructor(String.class, int.class).newInstance(clusterHost, 1);
    Set<Object> nodes = new LinkedHashSet<>(asList(clusterNode, unavailable));
    clusterTarget = clusterHost + ":1," + clusterHost + ":" + clusterPort;

    cluster =
        Class.forName("redis.clients.jedis.JedisCluster")
            .getConstructor(Set.class)
            .newInstance(nodes);
    cleanup.deferAfterAll(() -> cluster.getClass().getMethod("close").invoke(cluster));

    Field handlerField =
        Class.forName("redis.clients.jedis.BinaryJedisCluster")
            .getDeclaredField("connectionHandler");
    handlerField.setAccessible(true);
    clusterHandler = handlerField.get(cluster);
  }

  private static void assignSlotToNode(Object handler, int slot, Object node) throws Exception {
    try {
      handler
          .getClass()
          .getMethod("assignSlotToNode", int.class, node.getClass())
          .invoke(handler, slot, node);
    } catch (NoSuchMethodException ignored) {
      Field cacheField =
          Class.forName("redis.clients.jedis.JedisClusterConnectionHandler")
              .getDeclaredField("cache");
      cacheField.setAccessible(true);
      Object cache = cacheField.get(handler);
      cache
          .getClass()
          .getMethod("assignSlotToNode", int.class, node.getClass())
          .invoke(cache, slot, node);
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

  private static final class AskingServer implements AutoCloseable {
    private final ServerSocket serverSocket;
    private final Thread thread;
    private final int slot;
    private final int targetPort;
    private Socket socket;
    private Throwable failure;

    private AskingServer(int slot, int targetPort) throws IOException {
      this.slot = slot;
      this.targetPort = targetPort;
      serverSocket = new ServerSocket(0);
      thread = new Thread(this::serve, "jedis-asking-server");
      thread.setDaemon(true);
      thread.start();
    }

    private int getPort() {
      return serverSocket.getLocalPort();
    }

    private void awaitRequest() throws InterruptedException {
      thread.join(5_000);
      assertThat(thread.isAlive()).isFalse();
      assertThat(failure).isNull();
    }

    private void serve() {
      try {
        socket = serverSocket.accept();
        readCommand(socket.getInputStream());
        OutputStream output = socket.getOutputStream();
        output.write(("-ASK " + slot + " 127.0.0.1:" + targetPort + "\r\n").getBytes(US_ASCII));
        output.flush();
      } catch (Throwable t) {
        if (!serverSocket.isClosed()) {
          failure = t;
        }
      }
    }

    private static void readCommand(InputStream input) throws IOException {
      String arrayHeader = readLine(input);
      int count = Integer.parseInt(arrayHeader.substring(1));
      for (int i = 0; i < count; i++) {
        String bulkHeader = readLine(input);
        int length = Integer.parseInt(bulkHeader.substring(1));
        for (int j = 0; j < length + 2; j++) {
          if (input.read() == -1) {
            throw new IOException("Unexpected end of Redis command");
          }
        }
      }
    }

    private static String readLine(InputStream input) throws IOException {
      StringBuilder line = new StringBuilder();
      int previous = -1;
      int current;
      while ((current = input.read()) != -1) {
        if (previous == '\r' && current == '\n') {
          line.setLength(line.length() - 1);
          return line.toString();
        }
        line.append((char) current);
        previous = current;
      }
      throw new IOException("Unexpected end of Redis command");
    }

    @Override
    public void close() throws IOException {
      serverSocket.close();
      if (socket != null) {
        socket.close();
      }
    }
  }
}
