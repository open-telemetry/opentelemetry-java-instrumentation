/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.redisclient.v4_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldDatabaseSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.instrumentation.testing.junit.db.DbClientMetricsTestUtil.assertDurationMetric;
import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStable;
import static io.opentelemetry.instrumentation.testing.junit.service.SemconvServiceStabilityUtil.maybeStablePeerService;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.DbAttributes.DB_NAMESPACE;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_BATCH_SIZE;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_NAME;
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_TEXT;
import static io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_NAME;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_REDIS_DATABASE_INDEX;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_STATEMENT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues.REDIS;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.nCopies;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.redis.client.Command;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.RedisAPI;
import io.vertx.redis.client.RedisClientType;
import io.vertx.redis.client.RedisConnection;
import io.vertx.redis.client.RedisOptions;
import io.vertx.redis.client.Request;
import io.vertx.redis.client.Response;
import java.net.InetAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.containers.GenericContainer;

@SuppressWarnings("deprecation") // using deprecated semconv
class VertxRedisClientTest {
  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension
  private static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  private static final GenericContainer<?> redisServer =
      new GenericContainer<>("redis:6.2.3-alpine").withExposedPorts(6379);
  private static String host;
  private static String ip;
  private static int port;
  private static Vertx vertx;
  private static Redis client;
  private static RedisConnection connection;
  private static RedisAPI redis;
  private static Redis defaultDbClient;
  private static RedisAPI defaultDbRedis;

  @BeforeAll
  static void setup() throws Exception {
    redisServer.start();
    cleanup.deferAfterAll(redisServer::stop);

    host = redisServer.getHost();
    ip = InetAddress.getByName(host).getHostAddress();
    port = redisServer.getMappedPort(6379);

    vertx = Vertx.vertx();
    cleanup.deferAfterAll(vertx::close);
    client = Redis.createClient(vertx, "redis://" + host + ":" + port + "/1");
    cleanup.deferAfterAll(client::close);
    connection = client.connect().toCompletionStage().toCompletableFuture().get(30, SECONDS);
    redis = RedisAPI.api(connection);
    cleanup.deferAfterAll(redis::close);

    // a connection string without a database index connects to the default database 0
    defaultDbClient = Redis.createClient(vertx, "redis://" + host + ":" + port);
    cleanup.deferAfterAll(defaultDbClient::close);
    RedisConnection defaultDbConnection =
        defaultDbClient.connect().toCompletionStage().toCompletableFuture().get(30, SECONDS);
    defaultDbRedis = RedisAPI.api(defaultDbConnection);
    cleanup.deferAfterAll(defaultDbRedis::close);
  }

  @Test
  void setCommand() throws Exception {
    redis.set(asList("foo", "bar")).toCompletionStage().toCompletableFuture().get(30, SECONDS);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(emitStableDatabaseSemconv() ? "SET " + host + ":" + port : "SET")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(redisSpanAttributes("SET", "SET foo ?"))));

    assertDurationMetric(
        testing,
        "io.opentelemetry.vertx-redis-client-4.0",
        DB_SYSTEM_NAME,
        DB_OPERATION_NAME,
        DB_NAMESPACE,
        SERVER_ADDRESS,
        SERVER_PORT,
        NETWORK_PEER_ADDRESS,
        NETWORK_PEER_PORT);
  }

  @Test
  void setCommandOnDefaultDatabase() throws Exception {
    defaultDbRedis
        .set(asList("foo", "bar"))
        .toCompletionStage()
        .toCompletableFuture()
        .get(30, SECONDS);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(emitStableDatabaseSemconv() ? "SET " + host + ":" + port : "SET")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), REDIS),
                            equalTo(maybeStable(DB_STATEMENT), "SET foo ?"),
                            equalTo(maybeStable(DB_OPERATION), "SET"),
                            equalTo(DB_NAMESPACE, emitStableDatabaseSemconv() ? "0" : null),
                            equalTo(DB_REDIS_DATABASE_INDEX, null),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(maybeStablePeerService(), "test-peer-service"),
                            equalTo(NETWORK_PEER_PORT, port),
                            equalTo(NETWORK_PEER_ADDRESS, ip))));

    assertDurationMetric(
        testing,
        "io.opentelemetry.vertx-redis-client-4.0",
        DB_SYSTEM_NAME,
        DB_OPERATION_NAME,
        DB_NAMESPACE,
        SERVER_ADDRESS,
        SERVER_PORT,
        NETWORK_PEER_ADDRESS,
        NETWORK_PEER_PORT);
  }

  @Test
  void getCommand() throws Exception {
    redis.set(asList("foo", "bar")).toCompletionStage().toCompletableFuture().get(30, SECONDS);
    String value =
        redis.get("foo").toCompletionStage().toCompletableFuture().get(30, SECONDS).toString();

    assertThat(value).isEqualTo("bar");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(emitStableDatabaseSemconv() ? "SET " + host + ":" + port : "SET")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(redisSpanAttributes("SET", "SET foo ?"))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(emitStableDatabaseSemconv() ? "GET " + host + ":" + port : "GET")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(redisSpanAttributes("GET", "GET foo"))));
  }

  @Test
  void getCommandWithParent() throws Exception {
    redis.set(asList("foo", "bar")).toCompletionStage().toCompletableFuture().get(30, SECONDS);

    CompletableFuture<String> future = new CompletableFuture<>();
    CompletableFuture<String> result =
        future.whenComplete((value, throwable) -> testing.runWithSpan("callback", () -> {}));

    testing.runWithSpan(
        "parent",
        () ->
            redis
                .get("foo")
                .toCompletionStage()
                .toCompletableFuture()
                .whenComplete(
                    (response, throwable) -> {
                      if (throwable == null) {
                        future.complete(response.toString());
                      } else {
                        future.completeExceptionally(throwable);
                      }
                    }));

    String value = result.get(30, SECONDS);
    assertThat(value).isEqualTo("bar");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(emitStableDatabaseSemconv() ? "SET " + host + ":" + port : "SET")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(redisSpanAttributes("SET", "SET foo ?"))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL),
                span ->
                    span.hasName(emitStableDatabaseSemconv() ? "GET " + host + ":" + port : "GET")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(redisSpanAttributes("GET", "GET foo")),
                span ->
                    span.hasName("callback")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))));
  }

  @Test
  void commandWithNoArguments() throws Exception {
    redis.set(asList("foo", "bar")).toCompletionStage().toCompletableFuture().get(30, SECONDS);

    String value =
        redis.randomkey().toCompletionStage().toCompletableFuture().get(30, SECONDS).toString();

    assertThat(value).isEqualTo("foo");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(emitStableDatabaseSemconv() ? "SET " + host + ":" + port : "SET")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(redisSpanAttributes("SET", "SET foo ?"))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(
                            emitStableDatabaseSemconv()
                                ? "RANDOMKEY " + host + ":" + port
                                : "RANDOMKEY")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            redisSpanAttributes("RANDOMKEY", "RANDOMKEY"))));
  }

  @Test
  void emptyBatch() throws Exception {
    Future<List<Response>> future = connection.batch(emptyList());

    if (isVertx40x() && !future.isComplete()) {
      // Vert.x 4.0.x never completes an empty batch. Complete it only to clean up the test.
      assertThat(
              future
                  .getClass()
                  .getMethod("tryComplete", Object.class)
                  .invoke(future, (Object) null))
          .isEqualTo(true);
    } else {
      assertThat(future.toCompletionStage().toCompletableFuture().get(30, SECONDS)).isEmpty();
    }

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(
                            emitStableDatabaseSemconv()
                                ? "PIPELINE " + host + ":" + port
                                : "PIPELINE")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(redisSpanAttributes("PIPELINE", "", 0L))));
  }

  private static boolean isVertx40x() {
    try {
      return Class.forName(
                  "io.vertx.redis.client.impl.RedisConnectionManager$RedisConnectionProvider")
              .getDeclaredMethod("init", RedisConnection.class)
          != null;
    } catch (ReflectiveOperationException ignored) {
      return false;
    }
  }

  private static boolean isVertx5() {
    try {
      return Redis.class.getMethod(
              "createSentinelClient", Vertx.class, RedisOptions.class, Supplier.class)
          != null;
    } catch (ReflectiveOperationException ignored) {
      return false;
    }
  }

  @Test
  void dynamicClientOmitsTransientEndpoints() throws Exception {
    assumeTrue(isVertx5() && emitStableDatabaseSemconv());

    Object selectedOptions = redisStandaloneConnectOptions("redis://" + host + ":" + port);
    Object laterOptions = redisStandaloneConnectOptions("redis://later.example:1234");
    AtomicInteger supplierCalls = new AtomicInteger();
    Supplier<Future<Object>> optionsSupplier =
        () ->
            Future.succeededFuture(
                supplierCalls.getAndIncrement() == 0 ? selectedOptions : laterOptions);
    Redis dynamicClient =
        (Redis)
            Redis.class
                .getMethod(
                    "createStandaloneClient", Vertx.class, RedisOptions.class, Supplier.class)
                .invoke(null, vertx, new RedisOptions(), optionsSupplier);
    cleanup.deferCleanup(dynamicClient::close);

    RedisConnection dynamicConnection =
        dynamicClient.connect().toCompletionStage().toCompletableFuture().get(30, SECONDS);
    testing.waitForTraces(1);
    testing.clearData();

    assertThat(supplierCalls.get()).isGreaterThanOrEqualTo(2);
    dynamicConnection
        .send(Request.cmd(Command.GET).arg("dynamic-client"))
        .toCompletionStage()
        .toCompletableFuture()
        .get(30, SECONDS);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("GET")
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM_NAME, REDIS),
                            equalTo(DB_QUERY_TEXT, "GET dynamic-client"),
                            equalTo(DB_OPERATION_NAME, "GET"),
                            equalTo(DB_NAMESPACE, "0"),
                            equalTo(NETWORK_PEER_ADDRESS, ip),
                            equalTo(NETWORK_PEER_PORT, port))));
  }

  @Test
  void trailingInvalidClusterEndpointOmitsStableTarget() {
    assumeTrue(emitStableDatabaseSemconv());

    TestRedisCluster redisCluster = new TestRedisCluster();
    cleanup.deferCleanup(redisCluster);
    Redis clusterClient =
        Redis.createClient(
            vertx,
            new RedisOptions()
                .setType(RedisClientType.CLUSTER)
                .addConnectionString(
                    "redis://" + redisCluster.getHost() + ":" + redisCluster.getPort())
                .addConnectionString("redis://"));
    cleanup.deferCleanup(clusterClient::close);

    RedisConnection clusterConnection =
        clusterClient.connect().toCompletionStage().toCompletableFuture().join();
    cleanup.deferCleanup(clusterConnection::close);
    clusterConnection
        .send(Request.cmd(Command.SET).arg("invalid-cluster-endpoint").arg("value"))
        .toCompletionStage()
        .toCompletableFuture()
        .join();

    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              List<SpanData> spans =
                  testing.spans().stream()
                      .filter(span -> span.getName().equals("SET"))
                      .collect(toList());
              assertThat(spans).hasSize(1);
              assertThat(spans.get(0).getAttributes().get(SERVER_ADDRESS)).isNull();
              assertThat(spans.get(0).getAttributes().get(SERVER_PORT)).isNull();
              assertThat(spans.get(0).getAttributes().get(NETWORK_PEER_ADDRESS))
                  .isEqualTo(redisCluster.getHost());
              assertThat(spans.get(0).getAttributes().get(NETWORK_PEER_PORT))
                  .isEqualTo(Long.valueOf(redisCluster.getPort()));
            });
    redisCluster.assertNoFailure();
  }

  private static Object redisStandaloneConnectOptions(String connectionString)
      throws ReflectiveOperationException {
    Class<?> optionsClass = Class.forName("io.vertx.redis.client.RedisStandaloneConnectOptions");
    Object options = optionsClass.getConstructor().newInstance();
    optionsClass.getMethod("setConnectionString", String.class).invoke(options, connectionString);
    optionsClass.getMethod("setProtocolNegotiation", boolean.class).invoke(options, false);
    return options;
  }

  @Test
  void concurrentClientsKeepDistinctConfiguredTargets() throws Exception {
    assumeTrue(emitStableDatabaseSemconv());
    String secondHost = host.toUpperCase(Locale.ROOT);
    assumeTrue(!secondHost.equals(host));

    Redis firstClient = Redis.createClient(vertx, "redis://" + host + ":" + port);
    Redis secondClient = Redis.createClient(vertx, "redis://" + secondHost + ":" + port);
    cleanup.deferCleanup(firstClient::close);
    cleanup.deferCleanup(secondClient::close);

    CompletableFuture<?> first =
        firstClient
            .send(Request.cmd(Command.SET).arg("first-client").arg("value"))
            .toCompletionStage()
            .toCompletableFuture();
    CompletableFuture<?> second =
        secondClient
            .send(Request.cmd(Command.SET).arg("second-client").arg("value"))
            .toCompletionStage()
            .toCompletableFuture();
    CompletableFuture.allOf(first, second).get(30, SECONDS);

    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              List<SpanData> spans =
                  testing.spans().stream()
                      .filter(span -> span.getName().startsWith("SET "))
                      .collect(toList());
              assertThat(spans).hasSize(2);
              assertThat(spans)
                  .extracting(span -> span.getAttributes().get(SERVER_ADDRESS))
                  .containsExactlyInAnyOrder(host, secondHost);
              assertThat(spans)
                  .allSatisfy(
                      span -> {
                        assertThat(span.getAttributes().get(NETWORK_PEER_ADDRESS)).isEqualTo(ip);
                        assertThat(span.getAttributes().get(NETWORK_PEER_PORT))
                            .isEqualTo(Long.valueOf(port));
                      });
            });
  }

  @Test
  void sentinelClientIsScopedByItsEndpointsAndMaster() {
    Redis sentinelClient =
        Redis.createClient(
            vertx,
            new RedisOptions()
                .setType(RedisClientType.SENTINEL)
                .setMasterName("themaster")
                .setConnectionString("redis://" + host + ":" + port));
    cleanup.deferCleanup(sentinelClient::close);

    // A plain Redis server cannot complete Sentinel discovery, but the discovery command is traced.
    assertThatThrownBy(
            () ->
                sentinelClient.connect().toCompletionStage().toCompletableFuture().get(30, SECONDS))
        .isInstanceOf(ExecutionException.class);

    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              List<SpanData> spans =
                  testing.spans().stream()
                      .filter(span -> span.getName().contains("SENTINEL"))
                      .collect(toList());
              assertThat(spans).isNotEmpty();
              for (SpanData span : spans) {
                assertThat(span.getAttributes().get(SERVER_ADDRESS))
                    .isEqualTo(
                        emitStableDatabaseSemconv() ? host + ":" + port + "/themaster" : host);
                assertThat(span.getAttributes().get(SERVER_PORT))
                    .isEqualTo(emitStableDatabaseSemconv() ? null : Long.valueOf(port));
                assertThat(span.getAttributes().get(NETWORK_PEER_ADDRESS)).isEqualTo(ip);
                assertThat(span.getAttributes().get(NETWORK_PEER_PORT))
                    .isEqualTo(Long.valueOf(port));
              }
            });
  }

  @ParameterizedTest
  @MethodSource("batchScenarios")
  void batchCommand(BatchScenario scenario) throws Exception {
    connection.batch(scenario.requests).toCompletionStage().toCompletableFuture().get(30, SECONDS);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(
                            emitStableDatabaseSemconv()
                                ? scenario.operationName + " " + host + ":" + port
                                : scenario.operationName)
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            redisSpanAttributes(
                                scenario.operationName, scenario.queryText, scenario.batchSize))));
  }

  private static Stream<Arguments> batchScenarios() {
    String longBatchKey = String.join("", nCopies(1020, "x"));
    int batchSize = 33;
    int truncatedQueryTextCommandCount = 31;
    // No empty scenario: Vert.x Redis never completes client.batch(emptyList()),
    // and times out before asserting instrumentation.
    return Stream.of(
        argumentSet(
            "single",
            BatchScenario.builder()
                .addRequest(Request.cmd(Command.SET).arg("batch1").arg("v1"))
                .operationName("SET")
                .queryText("SET batch1 ?")
                .build()),
        argumentSet(
            "twoSameOperation",
            BatchScenario.builder()
                .addRequest(Request.cmd(Command.SET).arg("batch1").arg("v1"))
                .addRequest(Request.cmd(Command.SET).arg("batch2").arg("v2"))
                .operationName("PIPELINE SET")
                .queryText(
                    emitStableDatabaseSemconv()
                        ? "SET batch1 ?; SET batch2 ?"
                        : "SET batch1 ?;SET batch2 ?")
                .batchSize(2)
                .build()),
        argumentSet(
            "twoDifferentOperations",
            BatchScenario.builder()
                .addRequest(Request.cmd(Command.SET).arg("batch1").arg("v1"))
                .addRequest(Request.cmd(Command.GET).arg("batch1"))
                .operationName("PIPELINE")
                .queryText(
                    emitStableDatabaseSemconv()
                        ? "SET batch1 ?; GET batch1"
                        : "SET batch1 ?;GET batch1")
                .batchSize(2)
                .build()),
        argumentSet(
            "truncatedQueryText",
            BatchScenario.builder()
                .requests(
                    Stream.generate(() -> Request.cmd(Command.GET).arg(longBatchKey))
                        .limit(batchSize)
                        .collect(toList()))
                .operationName("PIPELINE GET")
                .queryText(
                    String.join(
                        emitStableDatabaseSemconv() ? "; " : ";",
                        nCopies(truncatedQueryTextCommandCount, "GET " + longBatchKey)))
                .batchSize(batchSize)
                .build()));
  }

  private static AttributeAssertion[] redisSpanAttributes(String operationName, String queryText) {
    return redisSpanAttributes(operationName, queryText, null);
  }

  private static AttributeAssertion[] redisSpanAttributes(
      String operationName, String queryText, Long batchSize) {
    List<AttributeAssertion> assertions = new ArrayList<>();
    if (emitOldDatabaseSemconv()) {
      assertions.add(equalTo(DB_SYSTEM, REDIS));
      assertions.add(equalTo(DB_STATEMENT, queryText));
      assertions.add(equalTo(DB_OPERATION, operationName));
      assertions.add(equalTo(DB_REDIS_DATABASE_INDEX, 1));
    }
    if (emitStableDatabaseSemconv()) {
      assertions.add(equalTo(DB_SYSTEM_NAME, REDIS));
      assertions.add(equalTo(DB_QUERY_TEXT, queryText));
      assertions.add(equalTo(DB_OPERATION_NAME, operationName));
      assertions.add(equalTo(DB_NAMESPACE, "1"));
      assertions.add(equalTo(DB_OPERATION_BATCH_SIZE, batchSize));
      assertions.add(equalTo(DB_NAME, emitOldDatabaseSemconv() ? "1" : null));
    }
    assertions.add(equalTo(SERVER_ADDRESS, host));
    assertions.add(equalTo(SERVER_PORT, port));
    assertions.add(equalTo(maybeStablePeerService(), "test-peer-service"));
    assertions.add(equalTo(NETWORK_PEER_PORT, port));
    assertions.add(equalTo(NETWORK_PEER_ADDRESS, ip));
    return assertions.toArray(new AttributeAssertion[0]);
  }

  private static class BatchScenario {
    private final List<Request> requests;
    private final String operationName;
    private final String queryText;
    private final Long batchSize;

    private BatchScenario(Builder builder) {
      this.requests = builder.requests;
      this.operationName = builder.operationName;
      this.queryText = builder.queryText;
      this.batchSize = builder.batchSize;
    }

    private static Builder builder() {
      return new Builder();
    }

    private static class Builder {
      private List<Request> requests = new ArrayList<>();
      private String operationName;
      private String queryText;
      private Long batchSize;

      private Builder addRequest(Request request) {
        requests.add(request);
        return this;
      }

      private Builder requests(List<Request> requests) {
        this.requests = requests;
        return this;
      }

      private Builder operationName(String operationName) {
        this.operationName = operationName;
        return this;
      }

      private Builder queryText(String queryText) {
        this.queryText = queryText;
        return this;
      }

      private Builder batchSize(long batchSize) {
        this.batchSize = batchSize;
        return this;
      }

      private BatchScenario build() {
        return new BatchScenario(this);
      }
    }
  }
}
