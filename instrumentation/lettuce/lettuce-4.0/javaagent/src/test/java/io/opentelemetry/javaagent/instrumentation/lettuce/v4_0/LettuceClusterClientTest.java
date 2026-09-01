/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v4_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStable;
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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.extension.RegisterExtension;

@SuppressWarnings("deprecation") // using deprecated semconv
@DisabledIfSystemProperty(
    named = "otel.instrumentation.lettuce.connection-telemetry.enabled",
    matches = "true")
class LettuceClusterClientTest {
  private static final String FIRST_NODE_ID = "0000000000000000000000000000000000000000";
  private static final String SECOND_NODE_ID = "1111111111111111111111111111111111111111";
  private static final int SLOT_SPLIT = SlotHash.SLOT_COUNT / 2;

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  private static TestRedisCluster firstRedisServer;
  private static TestRedisCluster secondRedisServer;
  private static StatefulRedisClusterConnection<String, String> connection;
  private static String host;
  private static int port;

  @BeforeAll
  static void setUp() throws Exception {
    firstRedisServer = new TestRedisCluster();
    cleanup.deferAfterAll(firstRedisServer);
    secondRedisServer = new TestRedisCluster();
    cleanup.deferAfterAll(secondRedisServer);

    host = firstRedisServer.getHost();
    port = firstRedisServer.getPort();

    RedisURI nodeUri = RedisURI.create("redis://" + host + ":" + port);
    RedisURI invalidSeedUri = RedisURI.create("redis://invalid:6379");
    invalidSeedUri.setHost("invalid,host");
    List<RedisURI> seedUris = asList(nodeUri, invalidSeedUri);
    List<RedisURI> nodeUris =
        asList(
            nodeUri,
            RedisURI.create(
                "redis://" + secondRedisServer.getHost() + ":" + secondRedisServer.getPort()));
    RedisClusterClient client = new TestRedisClusterClient(seedUris, nodeUris);
    cleanup.deferAfterAll(() -> client.shutdown(0, 15, SECONDS));

    connection = client.connect();
    cleanup.deferAfterAll(connection);
    testing.clearData();
  }

  @Test
  void invalidConfiguredSeedOmitsTargetForRoutedCommandsAndBatches() throws Exception {
    RedisAdvancedClusterAsyncCommands<String, String> asyncCommands = connection.async();
    String routedKey = keyInSlotRange("routed", SLOT_SPLIT, SlotHash.SLOT_COUNT);
    String firstBatchKey = keyInSlotRange("first-batch", 0, SLOT_SPLIT);
    String secondBatchKey = keyInSlotRange("second-batch", SLOT_SPLIT, SlotHash.SLOT_COUNT);
    assertThat(asyncCommands.set(routedKey, "value").get(10, SECONDS)).isEqualTo("OK");

    asyncCommands.setAutoFlushCommands(false);
    RedisFuture<String> first = asyncCommands.set(firstBatchKey, "value");
    RedisFuture<String> second = asyncCommands.set(secondBatchKey, "value");
    asyncCommands.flushCommands();
    asyncCommands.setAutoFlushCommands(true);
    assertThat(first.get(10, SECONDS)).isEqualTo("OK");
    assertThat(second.get(10, SECONDS)).isEqualTo("OK");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("SET")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(SERVER_ADDRESS, emitStableDatabaseSemconv() ? null : host),
                            equalTo(
                                SERVER_PORT,
                                emitStableDatabaseSemconv() ? null : Long.valueOf(port)),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                emitStableDatabaseSemconv() ? secondRedisServer.getHost() : null),
                            equalTo(
                                NETWORK_PEER_PORT,
                                emitStableDatabaseSemconv()
                                    ? Long.valueOf(secondRedisServer.getPort())
                                    : null),
                            equalTo(maybeStable(DB_SYSTEM), REDIS),
                            equalTo(DB_NAMESPACE, null),
                            equalTo(maybeStable(DB_OPERATION), "SET"))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("PIPELINE SET")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(SERVER_ADDRESS, emitStableDatabaseSemconv() ? null : host),
                            equalTo(
                                SERVER_PORT,
                                emitStableDatabaseSemconv() ? null : Long.valueOf(port)),
                            equalTo(NETWORK_PEER_ADDRESS, null),
                            equalTo(NETWORK_PEER_PORT, null),
                            equalTo(maybeStable(DB_SYSTEM), REDIS),
                            equalTo(DB_NAMESPACE, null),
                            equalTo(maybeStable(DB_OPERATION), "PIPELINE SET"),
                            equalTo(
                                DB_OPERATION_BATCH_SIZE,
                                emitStableDatabaseSemconv() ? Long.valueOf(2) : null))));

    firstRedisServer.assertReceivedSet(firstBatchKey);
    secondRedisServer.assertReceivedSet(routedKey, secondBatchKey);
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

    private TestRedisClusterClient(List<RedisURI> seedUris, List<RedisURI> nodeUris) {
      super(seedUris);
      this.nodeUris = nodeUris;
    }

    @Override
    protected Partitions loadPartitions() {
      Partitions partitions = new Partitions();
      partitions.addPartition(newNode(nodeUris.get(0), FIRST_NODE_ID, 0, SLOT_SPLIT));
      partitions.addPartition(
          newNode(nodeUris.get(1), SECOND_NODE_ID, SLOT_SPLIT, SlotHash.SLOT_COUNT));
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
        write(output, "+OK\r\n");
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
      for (Socket connection : snapshotConnections()) {
        connection.close();
      }
    }

    private List<Socket> snapshotConnections() {
      return new ArrayList<>(connections);
    }
  }
}
