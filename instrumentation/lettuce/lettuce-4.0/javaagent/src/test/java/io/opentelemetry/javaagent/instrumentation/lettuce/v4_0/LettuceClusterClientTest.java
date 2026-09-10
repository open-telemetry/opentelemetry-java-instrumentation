/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v4_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStable;
import static io.opentelemetry.instrumentation.testing.util.TestLatestDeps.testLatestDeps;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.DbAttributes.DB_NAMESPACE;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_BATCH_SIZE;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues.REDIS;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import com.lambdaworks.redis.RedisFuture;
import com.lambdaworks.redis.RedisURI;
import com.lambdaworks.redis.cluster.RedisClusterClient;
import com.lambdaworks.redis.cluster.SlotHash;
import com.lambdaworks.redis.cluster.api.StatefulRedisClusterConnection;
import com.lambdaworks.redis.cluster.api.async.RedisAdvancedClusterAsyncCommands;
import com.lambdaworks.redis.cluster.models.partitions.Partitions;
import com.lambdaworks.redis.cluster.models.partitions.RedisClusterNode;
import com.lambdaworks.redis.cluster.models.partitions.RedisClusterNode.NodeFlag;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.TraceAssert;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

@SuppressWarnings("deprecation") // using deprecated semconv
class LettuceClusterClientTest {
  private static final Logger logger = LoggerFactory.getLogger(LettuceClusterClientTest.class);

  private static final String FIRST_NODE_ID = "0000000000000000000000000000000000000000";
  private static final String SECOND_NODE_ID = "1111111111111111111111111111111111111111";
  private static final String PASSWORD = "password";
  private static final int SLOT_SPLIT = SlotHash.SLOT_COUNT / 2;

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  private static final DockerImageName CONTAINER_IMAGE =
      DockerImageName.parse("redis:6.2.3-alpine");

  private static final GenericContainer<?> redisServer =
      new GenericContainer<>(CONTAINER_IMAGE)
          .withExposedPorts(6379)
          .withLogConsumer(new Slf4jLogConsumer(logger))
          .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*", 1));

  private static StatefulRedisClusterConnection<String, String> connection;
  private static String host;
  private static String ip;
  private static int port;
  private static String configuredTarget;

  @BeforeAll
  static void setUp() throws Exception {
    redisServer.start();
    cleanup.deferAfterAll(redisServer::stop);

    host = redisServer.getHost();
    ip = InetAddress.getByName(host).getHostAddress();
    port = redisServer.getMappedPort(6379);

    RedisURI nodeUri = RedisURI.create("redis://" + host + ":" + port);
    RedisURI alternateSeed = RedisURI.create("redis://seed.invalid:6379");
    configuredTarget = "seed.invalid:6379," + host + ":" + port;
    RedisClusterClient client = new TestRedisClusterClient(asList(alternateSeed, nodeUri), nodeUri);
    cleanup.deferAfterAll(() -> client.shutdown(0, 15, SECONDS));

    connection = client.connect();
    cleanup.deferAfterAll(connection);

    if (testLatestDeps()) {
      testing.waitForTraces(1);
    }
  }

  @Test
  void testAuthenticationUsesConfiguredSeedList() throws Exception {
    GenericContainer<?> authenticatedRedisServer =
        new GenericContainer<>(CONTAINER_IMAGE)
            .withExposedPorts(6379)
            .withCommand("redis-server", "--requirepass", PASSWORD)
            .withLogConsumer(new Slf4jLogConsumer(logger))
            .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*", 1));
    authenticatedRedisServer.start();
    cleanup.deferCleanup(authenticatedRedisServer::stop);

    String authenticatedHost = authenticatedRedisServer.getHost();
    String authenticatedIp = InetAddress.getByName(authenticatedHost).getHostAddress();
    int authenticatedPort = authenticatedRedisServer.getMappedPort(6379);
    RedisURI nodeUri = RedisURI.create("redis://" + authenticatedHost + ":" + authenticatedPort);
    nodeUri.setPassword(PASSWORD);
    RedisURI alternateSeed = RedisURI.create("redis://seed.invalid:6379");
    alternateSeed.setPassword(PASSWORD);
    String authenticatedTarget = "seed.invalid:6379," + authenticatedHost + ":" + authenticatedPort;
    RedisClusterClient client = new TestRedisClusterClient(asList(alternateSeed, nodeUri), nodeUri);
    cleanup.deferCleanup(() -> client.shutdown(0, 15, SECONDS));
    cleanup.deferCleanup(client.connect());

    List<Consumer<TraceAssert>> traceAsserts = new ArrayList<>();
    traceAsserts.add(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(
                            emitStableDatabaseSemconv() ? "AUTH " + authenticatedTarget : "AUTH")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), REDIS),
                            equalTo(DB_NAMESPACE, null),
                            equalTo(maybeStable(DB_OPERATION), "AUTH"),
                            equalTo(
                                SERVER_ADDRESS,
                                emitStableDatabaseSemconv()
                                    ? authenticatedTarget
                                    : authenticatedHost),
                            equalTo(
                                SERVER_PORT,
                                emitStableDatabaseSemconv() ? null : (long) authenticatedPort),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                emitStableDatabaseSemconv() ? authenticatedIp : null),
                            equalTo(
                                NETWORK_PEER_PORT,
                                emitStableDatabaseSemconv()
                                    ? (long) authenticatedPort
                                    : null))));
    if (testLatestDeps()) {
      traceAsserts.add(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span ->
                      span.hasName(
                              emitStableDatabaseSemconv()
                                  ? "COMMAND " + authenticatedTarget
                                  : "COMMAND")
                          .hasKind(SpanKind.CLIENT)
                          .hasAttributesSatisfyingExactly(
                              equalTo(maybeStable(DB_SYSTEM), REDIS),
                              equalTo(DB_NAMESPACE, null),
                              equalTo(maybeStable(DB_OPERATION), "COMMAND"),
                              equalTo(
                                  SERVER_ADDRESS,
                                  emitStableDatabaseSemconv()
                                      ? authenticatedTarget
                                      : authenticatedHost),
                              equalTo(
                                  SERVER_PORT,
                                  emitStableDatabaseSemconv() ? null : (long) authenticatedPort),
                              equalTo(
                                  NETWORK_PEER_ADDRESS,
                                  emitStableDatabaseSemconv() ? authenticatedIp : null),
                              equalTo(
                                  NETWORK_PEER_PORT,
                                  emitStableDatabaseSemconv()
                                      ? (long) authenticatedPort
                                      : null))));
    }
    testing.waitAndAssertTraces(traceAsserts);
  }

  @Test
  void testCommandUsesConfiguredSeedList() throws Exception {
    RedisAdvancedClusterAsyncCommands<String, String> asyncCommands = connection.async();
    assertThat(asyncCommands.set("CLUSTER_COMMAND_KEY", "value").get(10, SECONDS)).isEqualTo("OK");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(emitStableDatabaseSemconv() ? "SET " + configuredTarget : "SET")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), REDIS),
                            equalTo(DB_NAMESPACE, null),
                            equalTo(maybeStable(DB_OPERATION), "SET"),
                            equalTo(
                                SERVER_ADDRESS,
                                emitStableDatabaseSemconv() ? configuredTarget : host),
                            equalTo(
                                SERVER_PORT, emitStableDatabaseSemconv() ? null : (long) port),
                            equalTo(
                                NETWORK_PEER_ADDRESS, emitStableDatabaseSemconv() ? ip : null),
                            equalTo(
                                NETWORK_PEER_PORT,
                                emitStableDatabaseSemconv() ? (long) port : null))));
  }

  @Test
  void testBatchUsesConfiguredSeedList() throws Exception {
    RedisAdvancedClusterAsyncCommands<String, String> asyncCommands = connection.async();
    asyncCommands.setAutoFlushCommands(false);
    cleanup.deferCleanup(() -> asyncCommands.setAutoFlushCommands(true));
    RedisFuture<String> first = asyncCommands.set("CLUSTER_BATCH_KEY_1", "value");
    RedisFuture<String> second = asyncCommands.set("CLUSTER_BATCH_KEY_2", "value");
    asyncCommands.flushCommands();
    assertThat(first.get(10, SECONDS)).isEqualTo("OK");
    assertThat(second.get(10, SECONDS)).isEqualTo("OK");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(
                            emitStableDatabaseSemconv()
                                ? "PIPELINE SET " + configuredTarget
                                : "PIPELINE SET")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), REDIS),
                            equalTo(DB_NAMESPACE, null),
                            equalTo(maybeStable(DB_OPERATION), "PIPELINE SET"),
                            equalTo(
                                SERVER_ADDRESS,
                                emitStableDatabaseSemconv() ? configuredTarget : host),
                            equalTo(SERVER_PORT, emitStableDatabaseSemconv() ? null : (long) port),
                            equalTo(
                                NETWORK_PEER_ADDRESS, emitStableDatabaseSemconv() ? ip : null),
                            equalTo(
                                NETWORK_PEER_PORT,
                                emitStableDatabaseSemconv() ? (long) port : null),
                            equalTo(
                                DB_OPERATION_BATCH_SIZE,
                                emitStableDatabaseSemconv() ? 2L : null))));
  }

  @Test
  void testCommandAndBatchUseObservedPeers() throws Exception {
    TestRedisCluster firstRedisServer = new TestRedisCluster();
    cleanup.deferCleanup(firstRedisServer);
    TestRedisCluster secondRedisServer = new TestRedisCluster();
    cleanup.deferCleanup(secondRedisServer);

    RedisURI firstNodeUri =
        RedisURI.create("redis://" + firstRedisServer.getHost() + ":" + firstRedisServer.getPort());
    RedisURI alternateSeed = RedisURI.create("redis://seed.invalid:6379");
    String peerConfiguredTarget =
        "seed.invalid:6379," + firstRedisServer.getHost() + ":" + firstRedisServer.getPort();
    List<RedisURI> nodeUris =
        asList(
            firstNodeUri,
            RedisURI.create(
                "redis://" + secondRedisServer.getHost() + ":" + secondRedisServer.getPort()));
    RedisClusterClient client =
        new TestRedisClusterClient(asList(alternateSeed, firstNodeUri), nodeUris);
    cleanup.deferCleanup(() -> client.shutdown(0, 15, SECONDS));
    StatefulRedisClusterConnection<String, String> peerConnection = client.connect();
    cleanup.deferCleanup(peerConnection);

    RedisAdvancedClusterAsyncCommands<String, String> asyncCommands = peerConnection.async();
    String routedKey = keyInSlotRange("routed", SLOT_SPLIT, SlotHash.SLOT_COUNT);
    String firstBatchKey = keyInSlotRange("first-batch", 0, SLOT_SPLIT);
    String secondBatchKey = keyInSlotRange("second-batch", SLOT_SPLIT, SlotHash.SLOT_COUNT);
    assertThat(asyncCommands.set(routedKey, "value").get(10, SECONDS)).isEqualTo("OK");

    asyncCommands.setAutoFlushCommands(false);
    cleanup.deferCleanup(() -> asyncCommands.setAutoFlushCommands(true));
    RedisFuture<String> first = asyncCommands.set(firstBatchKey, "value");
    RedisFuture<String> second = asyncCommands.set(secondBatchKey, "value");
    asyncCommands.flushCommands();
    assertThat(first.get(10, SECONDS)).isEqualTo("OK");
    assertThat(second.get(10, SECONDS)).isEqualTo("OK");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(
                            emitStableDatabaseSemconv() ? "SET " + peerConfiguredTarget : "SET")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), REDIS),
                            equalTo(DB_NAMESPACE, null),
                            equalTo(maybeStable(DB_OPERATION), "SET"),
                            equalTo(
                                SERVER_ADDRESS,
                                emitStableDatabaseSemconv()
                                    ? peerConfiguredTarget
                                    : firstRedisServer.getHost()),
                            equalTo(
                                SERVER_PORT,
                                emitStableDatabaseSemconv()
                                    ? null
                                    : (long)
                                        (testLatestDeps()
                                            ? secondRedisServer.getPort()
                                            : firstRedisServer.getPort())),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                emitStableDatabaseSemconv() ? secondRedisServer.getHost() : null),
                            equalTo(
                                NETWORK_PEER_PORT,
                                emitStableDatabaseSemconv()
                                    ? (long) secondRedisServer.getPort()
                                    : null))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(
                            emitStableDatabaseSemconv()
                                ? "PIPELINE SET " + peerConfiguredTarget
                                : "PIPELINE SET")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), REDIS),
                            equalTo(DB_NAMESPACE, null),
                            equalTo(maybeStable(DB_OPERATION), "PIPELINE SET"),
                            equalTo(
                                SERVER_ADDRESS,
                                emitStableDatabaseSemconv()
                                    ? peerConfiguredTarget
                                    : firstRedisServer.getHost()),
                            equalTo(
                                SERVER_PORT,
                                emitStableDatabaseSemconv()
                                    ? null
                                    : (long)
                                        (testLatestDeps()
                                            ? secondRedisServer.getPort()
                                            : firstRedisServer.getPort())),
                            equalTo(NETWORK_PEER_ADDRESS, null),
                            equalTo(NETWORK_PEER_PORT, null),
                            equalTo(
                                DB_OPERATION_BATCH_SIZE,
                                emitStableDatabaseSemconv() ? 2L : null))));

    firstRedisServer.assertReceivedSet(firstBatchKey);
    secondRedisServer.assertReceivedSet(routedKey, secondBatchKey);
    firstRedisServer.assertNoFailure();
    secondRedisServer.assertNoFailure();
  }

  // Lettuce 4.0 uses a Guava API that is not available in the test runtime when following
  // redirects.
  @Test
  @EnabledIfSystemProperty(named = "testLatestDeps", matches = "true")
  void redirectedCommandUsesLastPeer() throws Exception {
    TestRedisCluster firstRedisServer = new TestRedisCluster();
    cleanup.deferCleanup(firstRedisServer);
    TestRedisCluster secondRedisServer = new TestRedisCluster();
    cleanup.deferCleanup(secondRedisServer);

    RedisURI firstNodeUri =
        RedisURI.create("redis://" + firstRedisServer.getHost() + ":" + firstRedisServer.getPort());
    RedisURI alternateSeed = RedisURI.create("redis://seed.invalid:6379");
    String peerConfiguredTarget =
        "seed.invalid:6379," + firstRedisServer.getHost() + ":" + firstRedisServer.getPort();
    List<RedisURI> nodeUris =
        asList(
            firstNodeUri,
            RedisURI.create(
                "redis://" + secondRedisServer.getHost() + ":" + secondRedisServer.getPort()));
    RedisClusterClient client =
        new TestRedisClusterClient(asList(alternateSeed, firstNodeUri), nodeUris);
    cleanup.deferCleanup(() -> client.shutdown(0, 15, SECONDS));
    StatefulRedisClusterConnection<String, String> peerConnection = client.connect();
    cleanup.deferCleanup(peerConnection);

    RedisAdvancedClusterAsyncCommands<String, String> asyncCommands = peerConnection.async();
    String redirectedKey = keyInSlotRange("redirected", 0, SLOT_SPLIT);
    firstRedisServer.redirectSetOnce(redirectedKey, secondRedisServer);

    assertThat(asyncCommands.set(redirectedKey, "value").get(10, SECONDS)).isEqualTo("OK");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(
                            emitStableDatabaseSemconv() ? "SET " + peerConfiguredTarget : "SET")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), REDIS),
                            equalTo(DB_NAMESPACE, null),
                            equalTo(maybeStable(DB_OPERATION), "SET"),
                            equalTo(
                                SERVER_ADDRESS,
                                emitStableDatabaseSemconv()
                                    ? peerConfiguredTarget
                                    : firstRedisServer.getHost()),
                            equalTo(
                                SERVER_PORT,
                                emitStableDatabaseSemconv()
                                    ? null
                                    : Long.valueOf(secondRedisServer.getPort())),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                emitStableDatabaseSemconv() ? secondRedisServer.getHost() : null),
                            equalTo(
                                NETWORK_PEER_PORT,
                                emitStableDatabaseSemconv()
                                    ? Long.valueOf(secondRedisServer.getPort())
                                    : null))));

    firstRedisServer.assertReceivedSet(redirectedKey);
    secondRedisServer.assertReceivedSet(redirectedKey);
    firstRedisServer.assertNoFailure();
    secondRedisServer.assertNoFailure();
  }

  private static String keyInSlotRange(String prefix, int startInclusive, int endExclusive) {
    for (int i = 0; ; i++) {
      String key = prefix + "-" + i;
      int slot = SlotHash.getSlot(key);
      if (slot >= startInclusive && slot < endExclusive) {
        return key;
      }
    }
  }

  private static class TestRedisClusterClient extends RedisClusterClient {
    private final List<RedisURI> nodeUris;

    private TestRedisClusterClient(List<RedisURI> seedUris, RedisURI nodeUri) {
      this(seedUris, asList(nodeUri));
    }

    private TestRedisClusterClient(List<RedisURI> seedUris, List<RedisURI> nodeUris) {
      super(seedUris);
      this.nodeUris = nodeUris;
    }

    @Override
    protected Partitions loadPartitions() {
      Partitions partitions = new Partitions();
      if (nodeUris.size() == 1) {
        partitions.addPartition(
            newNode(nodeUris.get(0), FIRST_NODE_ID, 0, SlotHash.SLOT_COUNT));
      } else {
        partitions.addPartition(newNode(nodeUris.get(0), FIRST_NODE_ID, 0, SLOT_SPLIT));
        partitions.addPartition(
            newNode(nodeUris.get(1), SECOND_NODE_ID, SLOT_SPLIT, SlotHash.SLOT_COUNT));
      }
      partitions.updateCache();
      return partitions;
    }

    private static RedisClusterNode newNode(
        RedisURI uri, String nodeId, int startInclusive, int endExclusive) {
      List<Integer> slots = new ArrayList<>(endExclusive - startInclusive);
      for (int slot = startInclusive; slot < endExclusive; slot++) {
        slots.add(slot);
      }
      RedisClusterNode node = new RedisClusterNode();
      node.setUri(uri);
      node.setNodeId(nodeId);
      node.setConnected(true);
      node.setSlots(slots);
      node.setFlags(EnumSet.of(NodeFlag.MASTER));
      return node;
    }
  }

  private static class TestRedisCluster implements AutoCloseable {
    private final ServerSocket serverSocket;
    private final Set<Socket> connections = ConcurrentHashMap.newKeySet();
    private final Set<String> receivedSetKeys = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<String, TestRedisCluster> setRedirects =
        new ConcurrentHashMap<>();
    private final AtomicReference<Throwable> failure = new AtomicReference<>();
    private final Thread acceptThread;
    private volatile boolean closed;

    private TestRedisCluster() throws IOException {
      serverSocket = new ServerSocket(0, 50, InetAddress.getAllByName("127.0.0.1")[0]);
      acceptThread = new Thread(this::acceptConnections, "test-redis-cluster-accept");
      acceptThread.setDaemon(true);
      acceptThread.start();
    }

    private String getHost() {
      return serverSocket.getInetAddress().getHostAddress();
    }

    private int getPort() {
      return serverSocket.getLocalPort();
    }

    private void acceptConnections() {
      while (!closed) {
        try {
          Socket socket = serverSocket.accept();
          connections.add(socket);
          Thread thread =
              new Thread(() -> handleConnection(socket), "test-redis-cluster-connection");
          thread.setDaemon(true);
          thread.start();
        } catch (IOException e) {
          if (!closed) {
            failure.compareAndSet(null, e);
          }
        }
      }
    }

    private void handleConnection(Socket socket) {
      try (DataInputStream input = new DataInputStream(socket.getInputStream())) {
        OutputStream output = socket.getOutputStream();
        while (true) {
          List<String> command = readCommand(input);
          if (command.isEmpty()) {
            break;
          }
          writeResponse(command, output);
        }
      } catch (IOException e) {
        if (!closed) {
          failure.compareAndSet(null, e);
        }
      } finally {
        connections.remove(socket);
      }
    }

    private void writeResponse(List<String> command, OutputStream output) throws IOException {
      String name = command.get(0).toUpperCase(Locale.ROOT);
      if ("CLUSTER".equals(name)
          && command.size() > 1
          && "NODES".equals(command.get(1).toUpperCase(Locale.ROOT))) {
        String nodes =
            FIRST_NODE_ID
                + " "
                + getHost()
                + ":"
                + getPort()
                + " myself,master - 0 0 1 connected 0-16383\n";
        write(output, "$" + nodes.getBytes(UTF_8).length + "\r\n" + nodes + "\r\n");
      } else if ("SET".equals(name)) {
        String key = command.get(1);
        receivedSetKeys.add(key);
        TestRedisCluster redirect = setRedirects.remove(key);
        if (redirect == null) {
          write(output, "+OK\r\n");
        } else {
          write(
              output,
              "-MOVED "
                  + SlotHash.getSlot(key)
                  + " "
                  + redirect.getHost()
                  + ":"
                  + redirect.getPort()
                  + "\r\n");
        }
      } else if ("CLIENT".equals(name)) {
        write(output, "+OK\r\n");
      } else if ("COMMAND".equals(name)) {
        write(output, "*0\r\n");
      } else if ("PING".equals(name)) {
        write(output, "+PONG\r\n");
      } else {
        AssertionError error = new AssertionError("Unexpected Redis command: " + command);
        failure.compareAndSet(null, error);
        write(output, "-ERR unsupported command\r\n");
      }
    }

    private static List<String> readCommand(DataInputStream input) throws IOException {
      int first = input.read();
      if (first == -1) {
        return emptyList();
      }
      if (first != '*') {
        throw new IOException("Expected RESP array");
      }
      int argumentCount = Integer.parseInt(readLine(input));
      List<String> command = new ArrayList<>(argumentCount);
      for (int i = 0; i < argumentCount; i++) {
        if (input.read() != '$') {
          throw new IOException("Expected RESP bulk string");
        }
        int length = Integer.parseInt(readLine(input));
        byte[] value = new byte[length];
        input.readFully(value);
        if (input.read() != '\r' || input.read() != '\n') {
          throw new IOException("Expected RESP line ending");
        }
        command.add(new String(value, UTF_8));
      }
      return command;
    }

    private static String readLine(DataInputStream input) throws IOException {
      ByteArrayOutputStream line = new ByteArrayOutputStream();
      int value;
      while ((value = input.read()) != '\r') {
        if (value == -1) {
          throw new IOException("Unexpected end of RESP input");
        }
        line.write(value);
      }
      if (input.read() != '\n') {
        throw new IOException("Expected RESP line ending");
      }
      return new String(line.toByteArray(), US_ASCII);
    }

    private void redirectSetOnce(String key, TestRedisCluster target) {
      setRedirects.put(key, target);
    }

    private static void write(OutputStream output, String value) throws IOException {
      output.write(value.getBytes(UTF_8));
      output.flush();
    }

    private void assertNoFailure() {
      assertThat(failure.get()).isNull();
    }

    private void assertReceivedSet(String... keys) {
      assertThat(receivedSetKeys).contains(keys);
    }

    @Override
    public void close() throws IOException {
      closed = true;
      serverSocket.close();
      for (Socket connection : new ArrayList<>(connections)) {
        connection.close();
      }
    }
  }
}
