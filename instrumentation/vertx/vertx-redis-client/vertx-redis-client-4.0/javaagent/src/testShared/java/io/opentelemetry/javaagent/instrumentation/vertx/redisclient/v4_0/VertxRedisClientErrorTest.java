/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.redisclient.v4_0;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.instrumentation.testing.util.TestLatestDeps.testLatestDeps;
import static io.opentelemetry.instrumentation.testing.util.TestServerServiceMapping.expectedMappedServiceName;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.DbAttributes.DB_NAMESPACE;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_BATCH_SIZE;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_NAME;
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_TEXT;
import static io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.redis.client.Command;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.RedisConnection;
import io.vertx.redis.client.RedisOptions;
import io.vertx.redis.client.Request;
import io.vertx.redis.client.Response;
import io.vertx.redis.client.ResponseType;
import io.vertx.redis.client.impl.types.ErrorType;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class VertxRedisClientErrorTest {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.vertx-redis-client-4.0";
  private static final String HOST = "127.0.0.1";

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension
  private static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  private static Vertx vertx;

  private ReplyServer server;

  @BeforeAll
  static void setUp() {
    vertx = Vertx.vertx();
  }

  @AfterAll
  static void tearDown() throws ExecutionException, InterruptedException, TimeoutException {
    vertx.close().toCompletionStage().toCompletableFuture().get(30, SECONDS);
  }

  @ParameterizedTest
  @MethodSource("serverReplies")
  void singleServerReply(String reply, String expected)
      throws ExecutionException, IOException, InterruptedException, TimeoutException {
    RedisConnection connection = connect("-" + reply + "\r\n");

    Throwable error = failure(connection.send(Request.cmd(Command.GET).arg("key0")));

    assertThat(error)
        .isInstanceOf(ErrorType.class)
        .hasMessage(new String(reply.getBytes(UTF_8), ISO_8859_1));
    assertTelemetry(expected, 1);
  }

  private static Stream<Arguments> serverReplies() {
    String longPrefix = String.join("", Collections.nCopies(100, "x"));
    return Stream.of(
        argumentSet("ordinary", "WRONGTYPE detail", "WRONGTYPE"),
        argumentSet("wrapperLike", "ERR [0] application detail", "ERR"),
        argumentSet("clientLike", "CONNECTION_CLOSED detail", "CONNECTION_CLOSED"),
        argumentSet("parserLike", "ILLEGAL_STATE detail", "ILLEGAL_STATE"),
        argumentSet("prefixOnly", "PrefixOnly", "PrefixOnly"),
        argumentSet("leadingSpaces", "   Custom.Code! detail", "Custom.Code!"),
        argumentSet("tab", "custom\tcode detail", "custom\tcode"),
        argumentSet("unicode", "échec detail", "échec"),
        argumentSet("long", longPrefix + " detail", longPrefix),
        argumentSet("empty", "   ", ErrorType.class.getName()));
  }

  @ParameterizedTest
  @MethodSource("batchReplies")
  void batchServerReplies(String first, String second, String expected)
      throws ExecutionException, IOException, InterruptedException, TimeoutException {
    RedisConnection connection = connect("-" + first + "\r\n", "-" + second + "\r\n");

    Throwable error = failure(connection.batch(requests()));
    assertThat(error).isInstanceOf(ErrorType.class);

    String decodedFirstReply = new String(first.getBytes(UTF_8), ISO_8859_1);
    assertTelemetry(
        error.getMessage().equals(decodedFirstReply) ? expected : error.getClass().getName(), 2);
  }

  private static Stream<Arguments> batchReplies() {
    return Stream.of(
        argumentSet("prefixOnly", "First", "Second", "First"),
        argumentSet("originalErr", "ERR wrong number of arguments", "Second", "ERR"),
        argumentSet("unicode", "échec detail", "Second", "échec"),
        argumentSet("emptyFirst", "   ", "Second", ErrorType.class.getName()));
  }

  @ParameterizedTest
  @MethodSource("localFailures")
  void localDecoderFailure(String wireReply)
      throws ExecutionException, IOException, InterruptedException, TimeoutException {
    RedisConnection connection = connect(wireReply);

    assertThat(failure(connection.send(Request.cmd(Command.GET).arg("key0"))))
        .isInstanceOf(ErrorType.class);

    assertTelemetry(ErrorType.class.getName(), 1);
  }

  private static Stream<String> localFailures() {
    return Stream.of("?x\r\n", "#x\r\n", "*-2\r\n");
  }

  @Test
  void wrappedBatchTransportFailure()
      throws ExecutionException, IOException, InterruptedException, TimeoutException {
    RedisConnection connection = connect((String) null, null);

    assertThat(failure(connection.batch(requests()))).isInstanceOf(ErrorType.class);
    assertThat(server.requestCommands.get()).isEqualTo(2);

    assertTelemetry(ErrorType.class.getName(), 2);
  }

  @Test
  void batchQueueFailureUsesExceptionClass()
      throws ExecutionException, IOException, InterruptedException, TimeoutException {
    RedisConnection connection = connect(new RedisOptions().setMaxWaitingHandlers(2));

    Throwable error = failure(connection.batch(requests(3)));

    assertThat(error).isNotInstanceOf(ErrorType.class);
    assertTelemetry(error.getClass().getName(), 3);
  }

  @Test
  void terminalBatchFailureOwnsClassification()
      throws ExecutionException, IOException, InterruptedException, TimeoutException {
    RedisConnection connection = connect("-First detail\r\n", null);

    Throwable error = failure(connection.batch(requests()));

    assertThat(error).isInstanceOf(ErrorType.class);
    // 4.0.x terminates on the first server error; aggregating versions also await the client
    // failure and fall back to the aggregate exception class.
    assertTelemetry(
        "First detail".equals(error.getMessage()) ? "First" : error.getClass().getName(), 2);
  }

  @ParameterizedTest
  @MethodSource("responseCollections")
  void errorsInsideSingleResponseDoNotChangeApplicationSuccess(String wireReply, boolean map)
      throws ExecutionException, IOException, InterruptedException, TimeoutException {
    RedisConnection connection = connect(wireReply);

    Response response =
        connection
            .send(Request.cmd(Command.GET).arg("key0"))
            .toCompletionStage()
            .toCompletableFuture()
            .get(30, SECONDS);

    assertThat((map ? response.get("ok") : response.get(0)).toString()).isEqualTo("OK");
    assertThat((map ? response.get("errors") : response.get(1)).get(0))
        .isInstanceOf(ErrorType.class);
    assertTelemetry("Collection", 1);
  }

  @ParameterizedTest
  @MethodSource("responseCollections")
  void errorsInsideBatchResponsesDoNotChangeApplicationSuccess(String wireReply, boolean map)
      throws ExecutionException, IOException, InterruptedException, TimeoutException {
    RedisConnection connection = connect(wireReply, "+OK\r\n");

    List<Response> response =
        connection.batch(requests()).toCompletionStage().toCompletableFuture().get(30, SECONDS);

    assertThat(response).hasSize(2);
    Response first = response.get(0);
    assertThat((map ? first.get("errors") : first.get(1)).get(0)).isInstanceOf(ErrorType.class);
    assertThat(response.get(1).toString()).isEqualTo("OK");
    assertTelemetry("Collection", 2);
  }

  @Test
  void collidingBinaryMapKeysDoNotHideNestedServerError()
      throws ExecutionException, IOException, InterruptedException, TimeoutException {
    assumeTrue(testLatestDeps());
    RedisConnection connection = connectRaw(resp3MapWithCollidingBinaryKeys());

    Response response =
        connection
            .send(Request.cmd(Command.GET).arg("key0"))
            .toCompletionStage()
            .toCompletableFuture()
            .get(30, SECONDS);

    assertThat(response.type()).isEqualTo(ResponseType.MULTI);
    Iterator<Response> entries = response.iterator();
    Response first = entries.next();
    Response firstValue = first.get(first.getKeys().iterator().next());
    Response second = entries.next();
    Response secondValue = second.get(second.getKeys().iterator().next());
    assertThat(firstValue.get(0)).isInstanceOf(ErrorType.class);
    assertThat(secondValue.toString()).isEqualTo("OK");
    assertTelemetry("WRONGTYPE", 1);
  }

  private static Stream<Arguments> responseCollections() {
    return Stream.of(
        argumentSet("array", "*3\r\n+OK\r\n*1\r\n-Collection detail\r\n-Second detail\r\n", false),
        argumentSet(
            "map",
            "%3\r\n+ok\r\n+OK\r\n+errors\r\n*1\r\n-Collection detail\r\n+second\r\n-Second detail\r\n",
            true));
  }

  @Test
  void terminalServerErrorWinsOverEarlierCollectionError()
      throws ExecutionException, IOException, InterruptedException, TimeoutException {
    RedisConnection connection = connect("*1\r\n-Collection detail\r\n", "-Terminal detail\r\n");

    Throwable error = failure(connection.batch(requests()));
    assertThat(error).isInstanceOf(ErrorType.class);

    assertTelemetry(
        "Terminal detail".equals(error.getMessage()) ? "Terminal" : error.getClass().getName(), 2);
  }

  @Test
  void successAfterServerErrorHasNoErrorType()
      throws ExecutionException, IOException, InterruptedException, TimeoutException {
    RedisConnection connection = connect("-First detail\r\n", "+OK\r\n");
    failure(connection.send(Request.cmd(Command.GET).arg("key0")));
    assertTelemetry("First", 1);
    testing.clearData();

    Response response =
        connection
            .send(Request.cmd(Command.GET).arg("key0"))
            .toCompletionStage()
            .toCompletableFuture()
            .get(30, SECONDS);

    assertThat(response.toString()).isEqualTo("OK");
    assertTelemetry(null, 1);
  }

  @Test
  void batchSuccessHasNoErrorType()
      throws ExecutionException, IOException, InterruptedException, TimeoutException {
    RedisConnection connection = connect("+OK\r\n", "+OK\r\n");

    assertThat(
            connection.batch(requests()).toCompletionStage().toCompletableFuture().get(30, SECONDS))
        .extracting(Response::toString)
        .containsExactly("OK", "OK");

    assertTelemetry(null, 2);
  }

  @Test
  void deeplyNestedSuccessCompletesApplicationFuture()
      throws ExecutionException, IOException, InterruptedException, TimeoutException {
    int nesting = 10_000;
    StringBuilder reply = new StringBuilder(nesting * 5 + 5);
    for (int i = 0; i < nesting; i++) {
      reply.append("*1\r\n");
    }
    reply.append("+OK\r\n");

    RedisConnection connection =
        connect(new RedisOptions().setMaxNestedArrays(nesting), reply.toString());

    Response response =
        connection
            .send(Request.cmd(Command.GET).arg("key0"))
            .toCompletionStage()
            .toCompletableFuture()
            .get(30, SECONDS);
    for (int i = 0; i < nesting; i++) {
      response = response.get(0);
    }

    assertThat(response.toString()).isEqualTo("OK");
    assertTelemetry(null, 1);
  }

  private RedisConnection connect(String... replies)
      throws ExecutionException, IOException, InterruptedException, TimeoutException {
    return connect(new RedisOptions(), replies);
  }

  private RedisConnection connect(RedisOptions options, String... replies)
      throws ExecutionException, IOException, InterruptedException, TimeoutException {
    return connectWithReplies(
        options,
        Stream.of(replies)
            .map(reply -> reply == null ? null : reply.getBytes(UTF_8))
            .collect(toList()));
  }

  private RedisConnection connectRaw(byte[]... replies)
      throws ExecutionException, IOException, InterruptedException, TimeoutException {
    return connectWithReplies(new RedisOptions(), asList(replies));
  }

  private RedisConnection connectWithReplies(RedisOptions options, List<byte[]> replies)
      throws ExecutionException, IOException, InterruptedException, TimeoutException {
    server = new ReplyServer(replies);
    cleanup.deferCleanup(server);
    Redis client =
        Redis.createClient(
            vertx, options.setConnectionString("redis://" + HOST + ":" + server.port));
    cleanup.deferCleanup(client::close);
    RedisConnection connection =
        client.connect().toCompletionStage().toCompletableFuture().get(30, SECONDS);
    testing.waitForTraces(server.handshakeCommands.get());
    testing.clearData();
    return connection;
  }

  private static byte[] resp3MapWithCollidingBinaryKeys() throws IOException {
    ByteArrayOutputStream reply = new ByteArrayOutputStream();
    reply.write("%2\r\n$1\r\n".getBytes(US_ASCII));
    reply.write(0xff);
    reply.write("\r\n*1\r\n-WRONGTYPE detail\r\n$1\r\n".getBytes(US_ASCII));
    reply.write(0xfe);
    reply.write("\r\n+OK\r\n".getBytes(US_ASCII));
    return reply.toByteArray();
  }

  private static List<Request> requests() {
    return requests(2);
  }

  private static List<Request> requests(int count) {
    return IntStream.range(0, count)
        .mapToObj(index -> Request.cmd(Command.GET).arg("key" + index))
        .collect(toList());
  }

  private static Throwable failure(Future<?> future)
      throws ExecutionException, InterruptedException, TimeoutException {
    Throwable error =
        future
            .toCompletionStage()
            .handle((response, throwable) -> throwable)
            .toCompletableFuture()
            .get(30, SECONDS);
    assertThat(error).isNotNull();
    return error;
  }

  private void assertTelemetry(String errorType, int requestCount) {
    String operation = requestCount == 1 ? "GET" : "PIPELINE GET";
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(operation + " " + HOST + ":" + server.port)
                        .hasKind(SpanKind.CLIENT)
                        .hasStatus(errorType == null ? StatusData.unset() : StatusData.error())
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM_NAME, "redis"),
                            equalTo(DB_NAMESPACE, "0"),
                            equalTo(DB_OPERATION_NAME, operation),
                            equalTo(
                                DB_QUERY_TEXT,
                                IntStream.range(0, requestCount)
                                    .mapToObj(index -> "GET key" + index)
                                    .collect(joining("; "))),
                            equalTo(
                                DB_OPERATION_BATCH_SIZE,
                                requestCount == 1 ? null : (long) requestCount),
                            equalTo(SERVER_ADDRESS, HOST),
                            equalTo(SERVER_PORT, server.port),
                            equalTo(NETWORK_PEER_ADDRESS, HOST),
                            equalTo(NETWORK_PEER_PORT, server.port),
                            equalTo(stringKey("server.service.name"), expectedMappedServiceName()),
                            equalTo(ERROR_TYPE, errorType))));
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        metric ->
            metric
                .hasName("db.client.operation.duration")
                .hasHistogramSatisfying(
                    histogram ->
                        histogram.hasPointsSatisfying(
                            point ->
                                point
                                    .hasCount(1)
                                    .hasAttributesSatisfyingExactly(
                                        equalTo(DB_SYSTEM_NAME, "redis"),
                                        equalTo(DB_NAMESPACE, "0"),
                                        equalTo(DB_OPERATION_NAME, operation),
                                        equalTo(SERVER_ADDRESS, HOST),
                                        equalTo(SERVER_PORT, server.port),
                                        equalTo(NETWORK_PEER_ADDRESS, HOST),
                                        equalTo(NETWORK_PEER_PORT, server.port),
                                        equalTo(ERROR_TYPE, errorType)))));
    assertThat(server.failure.get()).isNull();
  }

  private static final class ReplyServer implements AutoCloseable {
    private final ServerSocket listener;
    private final int port;
    private final Thread thread;
    private final List<byte[]> replies;
    private final AtomicReference<Throwable> failure = new AtomicReference<>();
    private final AtomicInteger handshakeCommands = new AtomicInteger();
    private final AtomicInteger requestCommands = new AtomicInteger();
    private volatile Socket socket;
    private volatile boolean closed;

    private ReplyServer(List<byte[]> replies) throws IOException {
      this.replies = replies;
      listener = new ServerSocket(0, 1, InetAddress.getAllByName(HOST)[0]);
      port = listener.getLocalPort();
      thread = new Thread(this::run, "test-vertx-redis-error-replies");
      thread.setDaemon(true);
      thread.start();
    }

    private void run() {
      try (Socket connection = listener.accept();
          DataInputStream input = new DataInputStream(connection.getInputStream())) {
        socket = connection;
        OutputStream output = connection.getOutputStream();
        while (!closed) {
          List<String> command = TestRedisCluster.readCommand(input);
          if (command.isEmpty()) {
            return;
          }
          if (!command.get(0).equalsIgnoreCase("GET")) {
            handshakeCommands.incrementAndGet();
          }
          switch (command.get(0).toUpperCase(Locale.ROOT)) {
            case "HELLO":
              TestRedisCluster.write(output, "%1\r\n+proto\r\n:3\r\n");
              break;
            case "CLIENT":
            case "SELECT":
              TestRedisCluster.write(output, "+OK\r\n");
              break;
            case "COMMAND":
              TestRedisCluster.write(output, "*0\r\n");
              break;
            case "GET":
              int index = requestCommands.getAndIncrement();
              byte[] reply = replies.get(index);
              if (reply == null) {
                // Closing with unread pipelined requests can turn EOF into a TCP reset.
                if (index + 1 == replies.size()) {
                  return;
                }
              } else {
                output.write(reply);
                output.flush();
              }
              break;
            default:
              throw new AssertionError("Unexpected command: " + command);
          }
        }
      } catch (IOException e) {
        if (!closed) {
          failure.compareAndSet(null, e);
        }
      } catch (Throwable t) {
        failure.compareAndSet(null, t);
      }
    }

    @Override
    public void close() throws IOException, InterruptedException {
      closed = true;
      listener.close();
      Socket connection = socket;
      if (connection != null) {
        connection.close();
      }
      thread.join(SECONDS.toMillis(30));
      assertThat(thread.isAlive()).isFalse();
      assertThat(failure.get()).isNull();
    }
  }
}
