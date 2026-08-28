/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStable;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.DbAttributes.DB_NAMESPACE;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_BATCH_SIZE;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_STATEMENT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues.REDIS;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import io.lettuce.core.RedisFuture;
import io.lettuce.core.RedisURI;
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection;
import io.lettuce.core.cluster.api.async.RedisAdvancedClusterAsyncCommands;
import io.lettuce.core.cluster.api.reactive.RedisAdvancedClusterReactiveCommands;
import io.lettuce.core.cluster.api.sync.RedisAdvancedClusterCommands;
import io.lettuce.core.cluster.pubsub.StatefulRedisClusterPubSubConnection;
import io.lettuce.core.cluster.pubsub.api.reactive.RedisClusterPubSubReactiveCommands;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.test.utils.PortUtils;
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
  private static final String NODE_ID = "0000000000000000000000000000000000000000";

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  private static TestRedisCluster redisServer;
  private static StatefulRedisClusterConnection<String, String> connection;
  private static StatefulRedisClusterPubSubConnection<String, String> pubSubConnection;
  private static String configuredTarget;
  private static String host;
  private static int port;

  @BeforeAll
  static void setUp() throws Exception {
    redisServer = new TestRedisCluster();
    cleanup.deferAfterAll(redisServer);

    host = redisServer.getHost();
    port = redisServer.getPort();

    int unavailablePort = PortUtils.findOpenPort();
    configuredTarget = host + ":" + unavailablePort + "," + host + ":" + port;
    List<RedisURI> seedUris =
        asList(
            RedisURI.create("redis://" + host + ":" + unavailablePort),
            RedisURI.create("redis://" + host + ":" + port));
    RedisClusterClient client = RedisClusterClient.create(seedUris);
    cleanup.deferAfterAll(() -> client.shutdown(0, 15, SECONDS));

    connection = client.connect();
    cleanup.deferAfterAll(connection);
    pubSubConnection = client.connectPubSub();
    cleanup.deferAfterAll(pubSubConnection);
    testing.clearData();
  }

  @Test
  void configuredSeedsAreUsedForSyncBatchReactiveAndPubSubCommands() throws Exception {
    RedisAdvancedClusterCommands<String, String> syncCommands = connection.sync();
    assertThat(syncCommands.set("CLUSTER_COMMAND_KEY", "value")).isEqualTo("OK");

    connection.setAutoFlushCommands(false);
    RedisAdvancedClusterAsyncCommands<String, String> asyncCommands = connection.async();
    RedisFuture<String> first = asyncCommands.set("CLUSTER_BATCH_KEY_1", "value");
    RedisFuture<String> second = asyncCommands.set("CLUSTER_BATCH_KEY_2", "value");
    connection.flushCommands();
    connection.setAutoFlushCommands(true);
    assertThat(first.get(10, SECONDS)).isEqualTo("OK");
    assertThat(second.get(10, SECONDS)).isEqualTo("OK");

    RedisAdvancedClusterReactiveCommands<String, String> reactiveCommands = connection.reactive();
    assertThat(reactiveCommands.set("CLUSTER_REACTIVE_KEY", "value").block()).isEqualTo("OK");

    RedisClusterPubSubReactiveCommands<String, String> pubSubCommands = pubSubConnection.reactive();
    assertThat(pubSubCommands.publish("CLUSTER_CHANNEL", "message").block()).isZero();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(emitStableDatabaseSemconv() ? "SET " + configuredTarget : "SET")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                SERVER_ADDRESS,
                                emitStableDatabaseSemconv() ? configuredTarget : host),
                            equalTo(
                                SERVER_PORT,
                                emitStableDatabaseSemconv() ? null : Long.valueOf(port)),
                            equalTo(maybeStable(DB_SYSTEM), REDIS),
                            equalTo(DB_NAMESPACE, emitStableDatabaseSemconv() ? "0" : null),
                            equalTo(maybeStable(DB_STATEMENT), "SET CLUSTER_COMMAND_KEY ?"),
                            equalTo(maybeStable(DB_OPERATION), "SET"))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(
                            emitStableDatabaseSemconv()
                                ? "PIPELINE SET " + configuredTarget
                                : "PIPELINE SET")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                SERVER_ADDRESS,
                                emitStableDatabaseSemconv() ? configuredTarget : host),
                            equalTo(
                                SERVER_PORT,
                                emitStableDatabaseSemconv() ? null : Long.valueOf(port)),
                            equalTo(maybeStable(DB_SYSTEM), REDIS),
                            equalTo(DB_NAMESPACE, emitStableDatabaseSemconv() ? "0" : null),
                            equalTo(
                                maybeStable(DB_STATEMENT),
                                emitStableDatabaseSemconv()
                                    ? "SET CLUSTER_BATCH_KEY_1 ?; SET CLUSTER_BATCH_KEY_2 ?"
                                    : "SET CLUSTER_BATCH_KEY_1 ?;SET CLUSTER_BATCH_KEY_2 ?"),
                            equalTo(maybeStable(DB_OPERATION), "PIPELINE SET"),
                            equalTo(
                                DB_OPERATION_BATCH_SIZE,
                                emitStableDatabaseSemconv() ? Long.valueOf(2) : null))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(emitStableDatabaseSemconv() ? "SET " + configuredTarget : "SET")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                SERVER_ADDRESS,
                                emitStableDatabaseSemconv() ? configuredTarget : null),
                            equalTo(SERVER_PORT, null),
                            equalTo(maybeStable(DB_SYSTEM), REDIS),
                            equalTo(DB_NAMESPACE, null),
                            equalTo(maybeStable(DB_STATEMENT), "SET CLUSTER_REACTIVE_KEY ?"),
                            equalTo(maybeStable(DB_OPERATION), "SET"))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(
                            emitStableDatabaseSemconv() ? "PUBLISH " + configuredTarget : "PUBLISH")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                SERVER_ADDRESS,
                                emitStableDatabaseSemconv() ? configuredTarget : null),
                            equalTo(SERVER_PORT, null),
                            equalTo(maybeStable(DB_SYSTEM), REDIS),
                            equalTo(DB_NAMESPACE, null),
                            equalTo(maybeStable(DB_STATEMENT), "PUBLISH CLUSTER_CHANNEL ?"),
                            equalTo(maybeStable(DB_OPERATION), "PUBLISH"))));

    redisServer.assertNoFailure();
  }

  private static final class TestRedisCluster implements AutoCloseable {
    private final ServerSocket serverSocket;
    private final Set<Socket> connections = ConcurrentHashMap.newKeySet();
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
            NODE_ID
                + " "
                + getHost()
                + ":"
                + getPort()
                + " myself,master - 0 0 1 connected 0-16383\n";
        write(output, "$" + nodes.getBytes(UTF_8).length + "\r\n" + nodes + "\r\n");
      } else if ("CLUSTER".equals(name)
          && command.size() > 1
          && "MYID".equals(command.get(1).toUpperCase(Locale.ROOT))) {
        write(output, "$" + NODE_ID.length() + "\r\n" + NODE_ID + "\r\n");
      } else if ("SET".equals(name) || "CLIENT".equals(name)) {
        write(output, "+OK\r\n");
      } else if ("PUBLISH".equals(name)) {
        write(output, ":0\r\n");
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
