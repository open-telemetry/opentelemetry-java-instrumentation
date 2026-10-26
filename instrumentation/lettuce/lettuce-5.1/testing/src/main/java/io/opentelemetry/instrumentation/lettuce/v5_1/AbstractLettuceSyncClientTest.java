/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.lettuce.v5_1;

import static io.opentelemetry.instrumentation.testing.junit.db.DbClientMetricsTestUtil.assertDurationMetric;
import static io.opentelemetry.instrumentation.testing.util.TestLatestDeps.testLatestDeps;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.DbAttributes.DB_NAMESPACE;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_NAME;
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_TEXT;
import static io.opentelemetry.semconv.DbAttributes.DB_RESPONSE_STATUS_CODE;
import static io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues.REDIS;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisCommandExecutionException;
import io.lettuce.core.RedisConnectionException;
import io.lettuce.core.RedisException;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.RedisNoScriptException;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.TransactionResult;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.sdk.testing.assertj.TraceAssert;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation") // using deprecated semconv
public abstract class AbstractLettuceSyncClientTest extends AbstractLettuceClientTest {

  private String dbUriNonExistent;

  private static final Map<String, String> TEST_HASH_MAP;

  static {
    Map<String, String> map = new LinkedHashMap<>();
    map.put("firstname", "John");
    map.put("lastname", "Doe");
    map.put("age", "53");
    TEST_HASH_MAP = Collections.unmodifiableMap(map);
  }

  private RedisCommands<String, String> syncCommands;

  @BeforeAll
  void setUp() throws UnknownHostException {
    redisServer.start();
    cleanup.deferAfterAll(redisServer::stop);

    host = redisServer.getHost();
    ip = InetAddress.getByName(host).getHostAddress();
    port = redisServer.getMappedPort(6379);
    embeddedDbUri = "redis://" + host + ":" + port + "/" + DB_INDEX;

    int incorrectPort = PortUtils.findOpenPort();
    dbUriNonExistent = "redis://" + host + ":" + incorrectPort + "/" + DB_INDEX;

    redisClient = createClient(embeddedDbUri);
    cleanup.deferAfterAll(redisClient::shutdown);
    redisClient.setOptions(LettuceTestUtil.CLIENT_OPTIONS);

    connection = redisClient.connect();
    cleanup.deferAfterAll(connection);
    syncCommands = connection.sync();

    syncCommands.set("TESTKEY", "TESTVAL");
    syncCommands.hmset("TESTHM", TEST_HASH_MAP);

    // 2 sets
    testing().waitForTraces(2);
  }

  @Test
  void testConnect() {
    StatefulRedisConnection<String, String> testConnection = redisClient.connect();
    cleanup.deferCleanup(testConnection);

    if (testLatestDeps()) {
      // ignore CLIENT SETINFO traces
      testing().waitForTraces(2);
    } else {
      // Lettuce tracing does not trace connect
      assertThat(testing().spans()).isEmpty();
    }
  }

  @Test
  void testConnectException() {
    RedisClient testConnectionClient = RedisClient.create(dbUriNonExistent);
    testConnectionClient.setOptions(LettuceTestUtil.CLIENT_OPTIONS);
    cleanup.deferCleanup(testConnectionClient::shutdown);

    Throwable thrown = catchThrowable(testConnectionClient::connect);

    assertThat(thrown).isInstanceOf(RedisConnectionException.class);

    // Lettuce tracing does not trace connect
    assertThat(testing().spans()).isEmpty();
  }

  @Test
  void testSetCommand() {
    String res =
        testing().runWithSpan("parent", () -> syncCommands.set("TESTSETKEY", "TESTSETVAL"));
    assertThat(res).isEqualTo("OK");

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                    span ->
                        span.hasName(spanName("SET"))
                            .hasKind(SpanKind.CLIENT)
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfyingExactly(
                                addExtraAttributes(
                                    equalTo(NETWORK_PEER_ADDRESS, ip),
                                    equalTo(NETWORK_PEER_PORT, port),
                                    equalTo(SERVER_ADDRESS, host),
                                    equalTo(SERVER_PORT, port),
                                    equalTo(DB_SYSTEM_NAME, REDIS),
                                    equalTo(DB_QUERY_TEXT, "SET TESTSETKEY ?"),
                                    equalTo(DB_OPERATION_NAME, "SET")))));

    List<AttributeKey<?>> expected =
        new ArrayList<>(
            asList(
                DB_SYSTEM_NAME,
                DB_OPERATION_NAME,
                SERVER_ADDRESS,
                SERVER_PORT,
                NETWORK_PEER_ADDRESS,
                NETWORK_PEER_PORT));
    assertDurationMetric(testing(), "io.opentelemetry.lettuce-5.1", toArray(expected));
    assertCommandMetric("SET", null);
  }

  @SuppressWarnings("rawtypes")
  private static AttributeKey[] toArray(List<AttributeKey<?>> expected) {
    return expected.toArray(new AttributeKey[0]);
  }

  @Test
  void testGetCommand() {
    String res = testing().runWithSpan("parent", () -> syncCommands.get("TESTKEY"));
    assertThat(res).isEqualTo("TESTVAL");

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                    span ->
                        span.hasName(spanName("GET"))
                            .hasKind(SpanKind.CLIENT)
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfyingExactly(
                                addExtraAttributes(
                                    equalTo(NETWORK_PEER_ADDRESS, ip),
                                    equalTo(NETWORK_PEER_PORT, port),
                                    equalTo(SERVER_ADDRESS, host),
                                    equalTo(SERVER_PORT, port),
                                    equalTo(DB_SYSTEM_NAME, REDIS),
                                    equalTo(DB_QUERY_TEXT, "GET TESTKEY"),
                                    equalTo(DB_OPERATION_NAME, "GET")))));
  }

  @Test
  void testGetNonExistentKeyCommand() {
    String res = testing().runWithSpan("parent", () -> syncCommands.get("NON_EXISTENT_KEY"));
    assertThat(res).isNull();

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                    span ->
                        span.hasName(spanName("GET"))
                            .hasKind(SpanKind.CLIENT)
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfyingExactly(
                                addExtraAttributes(
                                    equalTo(NETWORK_PEER_ADDRESS, ip),
                                    equalTo(NETWORK_PEER_PORT, port),
                                    equalTo(SERVER_ADDRESS, host),
                                    equalTo(SERVER_PORT, port),
                                    equalTo(DB_SYSTEM_NAME, REDIS),
                                    equalTo(DB_QUERY_TEXT, "GET NON_EXISTENT_KEY"),
                                    equalTo(DB_OPERATION_NAME, "GET")))));
  }

  @Test
  void testCommandWithNoArguments() {
    String res = testing().runWithSpan("parent", () -> syncCommands.randomkey());
    assertThat(res).isNotNull();

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                    span ->
                        span.hasName(spanName("RANDOMKEY"))
                            .hasKind(SpanKind.CLIENT)
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfyingExactly(
                                addExtraAttributes(
                                    equalTo(NETWORK_PEER_ADDRESS, ip),
                                    equalTo(NETWORK_PEER_PORT, port),
                                    equalTo(SERVER_ADDRESS, host),
                                    equalTo(SERVER_PORT, port),
                                    equalTo(DB_SYSTEM_NAME, REDIS),
                                    equalTo(DB_QUERY_TEXT, "RANDOMKEY"),
                                    equalTo(DB_OPERATION_NAME, "RANDOMKEY")))));
  }

  @Test
  void testListCommand() {
    // Needs its own container or flaky from inconsistent command count
    ContainerConnection containerConnection = newContainerConnection();
    RedisCommands<String, String> commands = containerConnection.connection.sync();

    if (testLatestDeps()) {
      // ignore CLIENT SETINFO and MAINT_NOTIFICATIONS traces
      testing().waitForTraces(3);
      testing().clearData();
    }

    long res =
        testing().runWithSpan("parent", () -> commands.lpush("TESTLIST", "TESTLIST ELEMENT"));
    assertThat(res).isEqualTo(1);

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                    span ->
                        span.hasName(spanName("LPUSH", host, containerConnection.port))
                            .hasKind(SpanKind.CLIENT)
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfyingExactly(
                                addExtraAttributes(
                                    equalTo(NETWORK_PEER_ADDRESS, ip),
                                    equalTo(NETWORK_PEER_PORT, containerConnection.port),
                                    equalTo(SERVER_ADDRESS, host),
                                    equalTo(SERVER_PORT, containerConnection.port),
                                    equalTo(DB_SYSTEM_NAME, REDIS),
                                    equalTo(DB_QUERY_TEXT, "LPUSH TESTLIST ?"),
                                    equalTo(DB_OPERATION_NAME, "LPUSH")))));
  }

  @Test
  void testLpushWrongTypeCommandSetsSpanStatus() {
    syncCommands.set(WRONG_TYPE_KEY, WRONG_TYPE_VALUE);
    testing().waitForTraces(1);
    testing().clearData();

    Throwable thrown = catchThrowable(() -> syncCommands.lpush(WRONG_TYPE_KEY, WRONG_TYPE_VALUE));

    assertThat(thrown).isInstanceOf(RedisCommandExecutionException.class);

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName(spanName("LPUSH"))
                            .hasKind(SpanKind.CLIENT)
                            .hasStatus(StatusData.error())
                            .hasAttributesSatisfyingExactly(
                                addExtraAttributes(
                                    equalTo(NETWORK_PEER_ADDRESS, ip),
                                    equalTo(NETWORK_PEER_PORT, port),
                                    equalTo(SERVER_ADDRESS, host),
                                    equalTo(SERVER_PORT, port),
                                    equalTo(DB_SYSTEM_NAME, REDIS),
                                    equalTo(DB_QUERY_TEXT, "LPUSH " + WRONG_TYPE_KEY + " ?"),
                                    equalTo(DB_OPERATION_NAME, "LPUSH"),
                                    equalTo(
                                        ERROR_TYPE,
                                        testLatestDeps()
                                            ? RedisCommandExecutionException.class.getName()
                                            : "_OTHER"),
                                    equalTo(
                                        DB_RESPONSE_STATUS_CODE,
                                        testLatestDeps() ? "WRONGTYPE" : null)))
                            .satisfies(AbstractLettuceClientTest::assertCommandErrorEvents)));

    assertCommandMetric(
        "LPUSH", testLatestDeps() ? RedisCommandExecutionException.class.getName() : "_OTHER");
  }

  @Test
  void testEvalshaNoScriptError() {
    Throwable thrown =
        catchThrowable(
            () ->
                syncCommands.evalsha(
                    "0000000000000000000000000000000000000000", ScriptOutputType.VALUE));

    assertThat(thrown).isInstanceOf(RedisNoScriptException.class);

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName(spanName("EVALSHA"))
                            .hasKind(SpanKind.CLIENT)
                            .hasStatus(StatusData.error())
                            .hasAttributesSatisfyingExactly(
                                addExtraAttributes(
                                    equalTo(NETWORK_PEER_ADDRESS, ip),
                                    equalTo(NETWORK_PEER_PORT, port),
                                    equalTo(SERVER_ADDRESS, host),
                                    equalTo(SERVER_PORT, port),
                                    equalTo(DB_SYSTEM_NAME, REDIS),
                                    equalTo(
                                        DB_QUERY_TEXT,
                                        "EVALSHA 0000000000000000000000000000000000000000 0"),
                                    equalTo(DB_OPERATION_NAME, "EVALSHA"),
                                    equalTo(
                                        ERROR_TYPE,
                                        testLatestDeps()
                                            ? RedisNoScriptException.class.getName()
                                            : "_OTHER"),
                                    equalTo(
                                        DB_RESPONSE_STATUS_CODE,
                                        testLatestDeps() ? "NOSCRIPT" : null)))));

    assertCommandMetric(
        "EVALSHA", testLatestDeps() ? RedisNoScriptException.class.getName() : "_OTHER");
  }

  @Test
  void testServerErrorReplyWithTimeoutLookingDetails() {
    String errorMessage = "ERR upstream failed. Command timed out after 100 millisecond(s)";
    String script = "return redis.error_reply(ARGV[1])";
    String lettuceVersion = RedisClient.class.getPackage().getImplementationVersion();
    // Lettuce 6.0.0 supplies no remote endpoint to command-aware spans.
    boolean missingEndpoint = "6.0.0.RELEASE".equals(lettuceVersion);
    Throwable thrown =
        catchThrowable(
            () -> syncCommands.eval(script, ScriptOutputType.VALUE, new String[0], errorMessage));
    assertThat(thrown).isInstanceOf(RedisCommandExecutionException.class).hasMessage(errorMessage);

    String b64Script = Base64.getEncoder().encodeToString(script.getBytes(UTF_8));
    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName(missingEndpoint ? "EVAL" : spanName("EVAL"))
                            .hasKind(SpanKind.CLIENT)
                            .hasNoParent()
                            .hasStatus(StatusData.error())
                            .hasAttributesSatisfyingExactly(
                                addExtraAttributes(
                                    equalTo(NETWORK_PEER_ADDRESS, missingEndpoint ? null : ip),
                                    equalTo(
                                        NETWORK_PEER_PORT, missingEndpoint ? null : (long) port),
                                    equalTo(SERVER_ADDRESS, missingEndpoint ? null : host),
                                    equalTo(SERVER_PORT, missingEndpoint ? null : (long) port),
                                    equalTo(
                                        DB_NAMESPACE,
                                        lettuceVersion != null
                                                && lettuceVersion.startsWith("6.5.0.RELEASE")
                                            ? String.valueOf(DB_INDEX)
                                            : null),
                                    equalTo(DB_SYSTEM_NAME, REDIS),
                                    equalTo(DB_QUERY_TEXT, "EVAL " + b64Script + " 0 ?"),
                                    equalTo(DB_OPERATION_NAME, "EVAL"),
                                    equalTo(
                                        ERROR_TYPE,
                                        testLatestDeps()
                                            ? RedisCommandExecutionException.class.getName()
                                            : "_OTHER"),
                                    equalTo(
                                        DB_RESPONSE_STATUS_CODE,
                                        testLatestDeps() ? "ERR" : null)))));

    assertCommandMetric(
        "EVAL", testLatestDeps() ? RedisCommandExecutionException.class.getName() : "_OTHER");
  }

  @Test
  void testSuccessfulTransactionResults()
      throws ExecutionException, InterruptedException, TimeoutException {
    String lettuceVersion = RedisClient.class.getPackage().getImplementationVersion();
    boolean queuedAcknowledgements = !testLatestDeps() || "6.0.0.RELEASE".equals(lettuceVersion);
    String script = "return ARGV[1]";
    assertThat(syncCommands.multi()).isEqualTo("OK");
    RedisFuture<String> successful = connection.async().set("transaction-key", "value");
    RedisFuture<Object> evaluated =
        connection.async().eval(script, ScriptOutputType.VALUE, new String[0], "value");
    RedisFuture<String> last = connection.async().get("transaction-key");

    testing().waitForTraces(queuedAcknowledgements ? 4 : 1);
    assertThat(successful.toCompletableFuture()).isNotDone();
    assertThat(evaluated.toCompletableFuture()).isNotDone();
    assertThat(last.toCompletableFuture()).isNotDone();

    TransactionResult transaction = syncCommands.exec();
    assertThat(transaction).hasSize(3);
    assertThat(transaction.<String>get(0)).isEqualTo("OK");
    assertThat(successful.get(10, SECONDS)).isEqualTo("OK");
    assertThat(transaction.<String>get(1)).isEqualTo("value");
    assertThat(transaction.<String>get(2)).isEqualTo("value");
    assertThat(evaluated.get(10, SECONDS)).isEqualTo("value");
    assertThat(last.get(10, SECONDS)).isEqualTo("value");

    String encodedScript = Base64.getEncoder().encodeToString(script.getBytes(UTF_8));
    testing()
        .waitAndAssertTraces(
            transactionSpan("MULTI", "MULTI", StatusData.unset(), null, lettuceVersion),
            transactionSpan(
                "SET", "SET transaction-key ?", StatusData.unset(), null, lettuceVersion),
            transactionSpan(
                "EVAL", "EVAL " + encodedScript + " 0 ?", StatusData.unset(), null, lettuceVersion),
            transactionSpan("GET", "GET transaction-key", StatusData.unset(), null, lettuceVersion),
            transactionSpan("EXEC", "EXEC", StatusData.unset(), null, lettuceVersion));

    assertCommandMetric("MULTI", null);
    assertCommandMetric("SET", null);
    assertCommandMetric("EVAL", null);
    assertCommandMetric("GET", null);
    assertCommandMetric("EXEC", null);
  }

  @Test
  void testTransactionWithThrowableValue()
      throws ExecutionException, InterruptedException, TimeoutException {
    String lettuceVersion = RedisClient.class.getPackage().getImplementationVersion();
    boolean queuedAcknowledgements = !testLatestDeps() || "6.0.0.RELEASE".equals(lettuceVersion);
    StatefulRedisConnection<String, Object> throwableConnection = connectThrowableValueCodec();
    RedisCommands<String, Object> commands = throwableConnection.sync();
    commands.multi();
    RedisFuture<Object> get = throwableConnection.async().get("transaction-key");

    testing().waitForTraces(queuedAcknowledgements ? 2 : 1);
    assertThat(get.toCompletableFuture()).isNotDone();

    TransactionResult result = commands.exec();

    assertThat(result).hasSize(1);
    assertThat(result.<Throwable>get(0))
        .isInstanceOf(RedisCommandExecutionException.class)
        .hasMessage("decoded value");
    assertThat((Throwable) get.get(10, SECONDS))
        .isInstanceOf(RedisCommandExecutionException.class)
        .hasMessage("decoded value");

    testing()
        .waitAndAssertTraces(
            transactionSpan("MULTI", "MULTI", StatusData.unset(), null, lettuceVersion),
            transactionSpan("GET", "GET transaction-key", StatusData.unset(), null, lettuceVersion),
            transactionSpan("EXEC", "EXEC", StatusData.unset(), null, lettuceVersion));

    assertCommandMetric("MULTI", null);
    assertCommandMetric("GET", null);
    assertCommandMetric("EXEC", null);
  }

  @Test
  void testTransactionWithThrowableValueBeforeFailure()
      throws ExecutionException, InterruptedException, TimeoutException {
    String lettuceVersion = RedisClient.class.getPackage().getImplementationVersion();
    boolean queuedAcknowledgements = !testLatestDeps() || "6.0.0.RELEASE".equals(lettuceVersion);
    String errorMessage = "ERR transaction failed";
    String script = "return redis.error_reply(ARGV[1])";
    StatefulRedisConnection<String, Object> throwableConnection = connectThrowableValueCodec();
    RedisCommands<String, Object> commands = throwableConnection.sync();
    commands.multi();
    RedisFuture<Object> get = throwableConnection.async().get("transaction-key");
    RedisFuture<Object> evaluated =
        throwableConnection
            .async()
            .eval(script, ScriptOutputType.VALUE, new String[0], errorMessage);

    testing().waitForTraces(queuedAcknowledgements ? 3 : 1);
    assertThat(get.toCompletableFuture()).isNotDone();
    assertThat(evaluated.toCompletableFuture()).isNotDone();

    TransactionResult result = commands.exec();

    assertThat(result).hasSize(2);
    assertThat(result.<Throwable>get(0))
        .isInstanceOf(RedisCommandExecutionException.class)
        .hasMessage("decoded value");
    assertThat(result.<Throwable>get(1))
        .isInstanceOf(RedisCommandExecutionException.class)
        .hasMessage(errorMessage);
    assertThat((Throwable) get.get(10, SECONDS))
        .isInstanceOf(RedisCommandExecutionException.class)
        .hasMessage("decoded value");
    assertThatThrownBy(() -> evaluated.get(10, SECONDS))
        .hasCauseInstanceOf(RedisCommandExecutionException.class)
        .cause()
        .hasMessage(errorMessage);

    String encodedScript = Base64.getEncoder().encodeToString(script.getBytes(UTF_8));
    testing()
        .waitAndAssertTraces(
            transactionSpan("MULTI", "MULTI", StatusData.unset(), null, lettuceVersion),
            transactionSpan("GET", "GET transaction-key", StatusData.unset(), null, lettuceVersion),
            transactionSpan(
                "EVAL",
                "EVAL " + encodedScript + " 0 ?",
                testLatestDeps() && !queuedAcknowledgements
                    ? StatusData.error()
                    : StatusData.unset(),
                testLatestDeps() && !queuedAcknowledgements
                    ? RedisCommandExecutionException.class.getName()
                    : null,
                testLatestDeps() && !queuedAcknowledgements ? "ERR" : null,
                lettuceVersion),
            transactionSpan("EXEC", "EXEC", StatusData.unset(), null, lettuceVersion));

    assertCommandMetric("MULTI", null);
    assertCommandMetric("GET", null);
    assertCommandMetric(
        "EVAL",
        testLatestDeps() && !queuedAcknowledgements
            ? RedisCommandExecutionException.class.getName()
            : null);
    assertCommandMetric("EXEC", null);
  }

  private StatefulRedisConnection<String, Object> connectThrowableValueCodec() {
    syncCommands.set("transaction-key", "value");
    testing().waitForTraces(1);
    testing().clearData();

    RedisCodec<String, Object> codec =
        new RedisCodec<String, Object>() {
          @Override
          public String decodeKey(ByteBuffer bytes) {
            return StringCodec.UTF8.decodeKey(bytes);
          }

          @Override
          public Object decodeValue(ByteBuffer bytes) {
            return new RedisCommandExecutionException("decoded value");
          }

          @Override
          public ByteBuffer encodeKey(String key) {
            return StringCodec.UTF8.encodeKey(key);
          }

          @Override
          public ByteBuffer encodeValue(Object value) {
            return StringCodec.UTF8.encodeValue(value.toString());
          }
        };
    StatefulRedisConnection<String, Object> throwableConnection = redisClient.connect(codec);
    cleanup.deferCleanup(throwableConnection);
    if (testLatestDeps()) {
      testing().waitForTraces(3);
      testing().clearData();
    }
    return throwableConnection;
  }

  @Test
  void testTransactionResultsWithFailures()
      throws ExecutionException, InterruptedException, TimeoutException {
    String lettuceVersion = RedisClient.class.getPackage().getImplementationVersion();
    boolean queuedAcknowledgements = !testLatestDeps() || "6.0.0.RELEASE".equals(lettuceVersion);
    String errorMessage = "ERR transaction failed";
    String script = "return redis.error_reply(ARGV[1])";
    assertThat(syncCommands.multi()).isEqualTo("OK");
    RedisFuture<String> successful = connection.async().set("transaction-key", "value");
    RedisFuture<Object> evaluated =
        connection.async().eval(script, ScriptOutputType.VALUE, new String[0], errorMessage);
    RedisFuture<?> last = connection.async().evalsha("00000000", ScriptOutputType.VALUE);

    testing().waitForTraces(queuedAcknowledgements ? 4 : 1);
    assertThat(successful.toCompletableFuture()).isNotDone();
    assertThat(evaluated.toCompletableFuture()).isNotDone();
    assertThat(last.toCompletableFuture()).isNotDone();

    TransactionResult transaction = syncCommands.exec();
    assertThat(transaction).hasSize(3);
    assertThat(transaction.<String>get(0)).isEqualTo("OK");
    assertThat(successful.get(10, SECONDS)).isEqualTo("OK");
    assertThat(transaction.<Throwable>get(1))
        .isInstanceOf(RedisCommandExecutionException.class)
        .hasMessage(errorMessage);
    assertThat(transaction.<Throwable>get(2)).isInstanceOf(RedisNoScriptException.class);
    assertThatThrownBy(() -> evaluated.get(10, SECONDS))
        .hasCauseInstanceOf(RedisCommandExecutionException.class)
        .cause()
        .hasMessage(errorMessage);
    assertThatThrownBy(() -> last.get(10, SECONDS))
        .hasCauseInstanceOf(RedisNoScriptException.class);

    String encodedScript = Base64.getEncoder().encodeToString(script.getBytes(UTF_8));
    testing()
        .waitAndAssertTraces(
            transactionSpan("MULTI", "MULTI", StatusData.unset(), null, lettuceVersion),
            transactionSpan(
                "SET", "SET transaction-key ?", StatusData.unset(), null, lettuceVersion),
            transactionSpan(
                "EVAL",
                "EVAL " + encodedScript + " 0 ?",
                testLatestDeps() && !queuedAcknowledgements
                    ? StatusData.error()
                    : StatusData.unset(),
                testLatestDeps() && !queuedAcknowledgements
                    ? RedisCommandExecutionException.class.getName()
                    : null,
                testLatestDeps() && !queuedAcknowledgements ? "ERR" : null,
                lettuceVersion),
            transactionSpan(
                "EVALSHA",
                "EVALSHA 00000000 0",
                testLatestDeps() && !queuedAcknowledgements
                    ? StatusData.error()
                    : StatusData.unset(),
                testLatestDeps() && !queuedAcknowledgements
                    ? RedisNoScriptException.class.getName()
                    : null,
                testLatestDeps() && !queuedAcknowledgements ? "NOSCRIPT" : null,
                lettuceVersion),
            transactionSpan("EXEC", "EXEC", StatusData.unset(), null, lettuceVersion));

    assertCommandMetric("MULTI", null);
    assertCommandMetric("SET", null);
    assertCommandMetric(
        "EVAL",
        testLatestDeps() && !queuedAcknowledgements
            ? RedisCommandExecutionException.class.getName()
            : null);
    assertCommandMetric(
        "EVALSHA",
        testLatestDeps() && !queuedAcknowledgements
            ? RedisNoScriptException.class.getName()
            : null);
    assertCommandMetric("EXEC", null);
  }

  private Consumer<TraceAssert> transactionSpan(
      String operation, String query, StatusData status, String errorType, String lettuceVersion) {
    return transactionSpan(operation, query, status, errorType, null, lettuceVersion);
  }

  private Consumer<TraceAssert> transactionSpan(
      String operation,
      String query,
      StatusData status,
      String errorType,
      String responseStatusCode,
      String lettuceVersion) {
    boolean missingEndpoint = "6.0.0.RELEASE".equals(lettuceVersion);
    return trace ->
        trace.hasSpansSatisfyingExactly(
            span ->
                span.hasName(missingEndpoint ? operation : spanName(operation))
                    .hasKind(SpanKind.CLIENT)
                    .hasNoParent()
                    .hasStatus(status)
                    .hasAttributesSatisfyingExactly(
                        equalTo(DB_SYSTEM_NAME, REDIS),
                        equalTo(DB_QUERY_TEXT, query),
                        equalTo(DB_OPERATION_NAME, operation),
                        equalTo(NETWORK_PEER_ADDRESS, missingEndpoint ? null : ip),
                        equalTo(NETWORK_PEER_PORT, missingEndpoint ? null : (long) port),
                        equalTo(SERVER_ADDRESS, missingEndpoint ? null : host),
                        equalTo(SERVER_PORT, missingEndpoint ? null : (long) port),
                        equalTo(
                            DB_NAMESPACE,
                            lettuceVersion != null && lettuceVersion.startsWith("6.5.0.RELEASE")
                                ? String.valueOf(DB_INDEX)
                                : null),
                        equalTo(ERROR_TYPE, errorType),
                        equalTo(DB_RESPONSE_STATUS_CODE, responseStatusCode)));
  }

  @Test
  void testHashSetCommand() {
    String res = testing().runWithSpan("parent", () -> syncCommands.hmset("user", TEST_HASH_MAP));
    assertThat(res).isEqualTo("OK");

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                    span ->
                        span.hasName(spanName("HMSET"))
                            .hasKind(SpanKind.CLIENT)
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfyingExactly(
                                addExtraAttributes(
                                    equalTo(NETWORK_PEER_ADDRESS, ip),
                                    equalTo(NETWORK_PEER_PORT, port),
                                    equalTo(SERVER_ADDRESS, host),
                                    equalTo(SERVER_PORT, port),
                                    equalTo(DB_SYSTEM_NAME, REDIS),
                                    equalTo(
                                        DB_QUERY_TEXT, "HMSET user firstname ? lastname ? age ?"),
                                    equalTo(DB_OPERATION_NAME, "HMSET")))));
  }

  @Test
  void testHashGetallCommand() {
    Map<String, String> res = testing().runWithSpan("parent", () -> syncCommands.hgetall("TESTHM"));
    assertThat(res).isEqualTo(TEST_HASH_MAP);

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                    span ->
                        span.hasName(spanName("HGETALL"))
                            .hasKind(SpanKind.CLIENT)
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfyingExactly(
                                addExtraAttributes(
                                    equalTo(NETWORK_PEER_ADDRESS, ip),
                                    equalTo(NETWORK_PEER_PORT, port),
                                    equalTo(SERVER_ADDRESS, host),
                                    equalTo(SERVER_PORT, port),
                                    equalTo(DB_SYSTEM_NAME, REDIS),
                                    equalTo(DB_QUERY_TEXT, "HGETALL TESTHM"),
                                    equalTo(DB_OPERATION_NAME, "HGETALL")))));
  }

  @Test
  void testEvalCommand() {
    String script =
        "redis.call('lpush', KEYS[1], ARGV[1], ARGV[2]); return redis.call('llen', KEYS[1])";

    Long result =
        testing()
            .runWithSpan(
                "parent",
                () ->
                    syncCommands.eval(
                        script, ScriptOutputType.INTEGER, new String[] {"TESTLIST"}, "abc", "def"));
    assertThat(result).isEqualTo(2);

    String b64Script = Base64.getEncoder().encodeToString(script.getBytes(UTF_8));

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                    span ->
                        span.hasName(spanName("EVAL"))
                            .hasKind(SpanKind.CLIENT)
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfyingExactly(
                                addExtraAttributes(
                                    equalTo(NETWORK_PEER_ADDRESS, ip),
                                    equalTo(NETWORK_PEER_PORT, port),
                                    equalTo(SERVER_ADDRESS, host),
                                    equalTo(SERVER_PORT, port),
                                    equalTo(DB_SYSTEM_NAME, REDIS),
                                    equalTo(DB_QUERY_TEXT, "EVAL " + b64Script + " 1 TESTLIST ? ?"),
                                    equalTo(DB_OPERATION_NAME, "EVAL")))));
  }

  @Test
  void testMsetCommand() {
    Map<String, String> values = new LinkedHashMap<>();
    values.put("key1", "value1");
    values.put("key2", "value2");
    String result = syncCommands.mset(values);

    assertThat(result).isEqualTo("OK");

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName(spanName("MSET"))
                            .hasKind(SpanKind.CLIENT)
                            .hasAttributesSatisfyingExactly(
                                addExtraAttributes(
                                    equalTo(NETWORK_PEER_ADDRESS, ip),
                                    equalTo(NETWORK_PEER_PORT, port),
                                    equalTo(SERVER_ADDRESS, host),
                                    equalTo(SERVER_PORT, port),
                                    equalTo(DB_SYSTEM_NAME, REDIS),
                                    equalTo(DB_QUERY_TEXT, "MSET key1 ? key2 ?"),
                                    equalTo(DB_OPERATION_NAME, "MSET")))));
  }

  @Test
  void testDebugSegfaultCommandWithNoArgumentProducesNoSpan() {
    // Test causes redis to crash therefore it needs its own container
    ContainerConnection containerConnection = newContainerConnection();
    RedisCommands<String, String> commands = containerConnection.connection.sync();

    commands.debugSegfault();

    if (testLatestDeps()) {
      testing()
          .waitAndAssertTraces(
              trace ->
                  trace.hasSpansSatisfyingExactly(
                      span ->
                          span.hasName(spanName("CLIENT", host, containerConnection.port))
                              .hasKind(SpanKind.CLIENT)
                              .hasAttributesSatisfyingExactly(
                                  addExtraAttributes(
                                      equalTo(NETWORK_PEER_ADDRESS, ip),
                                      equalTo(NETWORK_PEER_PORT, containerConnection.port),
                                      equalTo(SERVER_ADDRESS, host),
                                      equalTo(SERVER_PORT, containerConnection.port),
                                      equalTo(DB_SYSTEM_NAME, REDIS),
                                      equalTo(DB_QUERY_TEXT, "CLIENT SETINFO lib-name Lettuce"),
                                      equalTo(DB_OPERATION_NAME, "CLIENT"),
                                      equalTo(
                                          ERROR_TYPE,
                                          RedisCommandExecutionException.class.getName()),
                                      equalTo(DB_RESPONSE_STATUS_CODE, "ERR")))),
              trace ->
                  trace.hasSpansSatisfyingExactly(
                      span ->
                          span.hasName(spanName("CLIENT", host, containerConnection.port))
                              .hasKind(SpanKind.CLIENT)
                              .hasAttributesSatisfyingExactly(
                                  addExtraAttributes(
                                      equalTo(NETWORK_PEER_ADDRESS, ip),
                                      equalTo(NETWORK_PEER_PORT, containerConnection.port),
                                      equalTo(SERVER_ADDRESS, host),
                                      equalTo(SERVER_PORT, containerConnection.port),
                                      equalTo(DB_SYSTEM_NAME, REDIS),
                                      satisfies(
                                          DB_QUERY_TEXT,
                                          val -> val.startsWith("CLIENT SETINFO lib-ver")),
                                      equalTo(DB_OPERATION_NAME, "CLIENT"),
                                      equalTo(
                                          ERROR_TYPE,
                                          RedisCommandExecutionException.class.getName()),
                                      equalTo(DB_RESPONSE_STATUS_CODE, "ERR")))),
              trace ->
                  trace.hasSpansSatisfyingExactly(
                      span ->
                          span.hasName(spanName("CLIENT", host, containerConnection.port))
                              .hasKind(SpanKind.CLIENT)
                              .hasAttributesSatisfyingExactly(
                                  addExtraAttributes(
                                      equalTo(NETWORK_PEER_ADDRESS, ip),
                                      equalTo(NETWORK_PEER_PORT, containerConnection.port),
                                      equalTo(SERVER_ADDRESS, host),
                                      equalTo(SERVER_PORT, containerConnection.port),
                                      equalTo(DB_SYSTEM_NAME, REDIS),
                                      satisfies(
                                          DB_QUERY_TEXT,
                                          val -> val.startsWith("CLIENT MAINT_NOTIFICATIONS")),
                                      equalTo(DB_OPERATION_NAME, "CLIENT"),
                                      equalTo(
                                          ERROR_TYPE,
                                          RedisCommandExecutionException.class.getName()),
                                      equalTo(DB_RESPONSE_STATUS_CODE, "ERR")))),
              trace ->
                  trace.hasSpansSatisfyingExactly(
                      span ->
                          span.hasName(spanName("DEBUG", host, containerConnection.port))
                              .hasKind(SpanKind.CLIENT)
                              .hasAttributesSatisfyingExactly(
                                  addExtraAttributes(
                                      equalTo(NETWORK_PEER_ADDRESS, ip),
                                      equalTo(NETWORK_PEER_PORT, containerConnection.port),
                                      equalTo(SERVER_ADDRESS, host),
                                      equalTo(SERVER_PORT, containerConnection.port),
                                      equalTo(DB_SYSTEM_NAME, REDIS),
                                      equalTo(DB_QUERY_TEXT, "DEBUG SEGFAULT"),
                                      equalTo(DB_OPERATION_NAME, "DEBUG")))));
    } else {
      testing()
          .waitAndAssertTraces(
              trace ->
                  trace.hasSpansSatisfyingExactly(
                      span ->
                          span.hasName(spanName("DEBUG", host, containerConnection.port))
                              .hasKind(SpanKind.CLIENT)
                              .hasAttributesSatisfyingExactly(
                                  addExtraAttributes(
                                      equalTo(NETWORK_PEER_ADDRESS, ip),
                                      equalTo(NETWORK_PEER_PORT, containerConnection.port),
                                      equalTo(SERVER_ADDRESS, host),
                                      equalTo(SERVER_PORT, containerConnection.port),
                                      equalTo(DB_SYSTEM_NAME, REDIS),
                                      equalTo(DB_QUERY_TEXT, "DEBUG SEGFAULT"),
                                      equalTo(DB_OPERATION_NAME, "DEBUG")))));
    }
  }

  @Test
  void testShutdownCommandProducesNoSpan() {
    // Test causes redis to crash therefore it needs its own container
    ContainerConnection containerConnection = newContainerConnection();
    RedisCommands<String, String> commands = containerConnection.connection.sync();

    if (testLatestDeps()) {
      // ignore CLIENT SETINFO and MAINT_NOTIFICATIONS traces
      testing().waitForTraces(3);
      testing().clearData();
    }

    commands.shutdown(false);

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> {
                      span.hasName(spanName("SHUTDOWN", host, containerConnection.port))
                          .hasKind(SpanKind.CLIENT)
                          .hasStatus(StatusData.error())
                          .hasAttributesSatisfyingExactly(
                              addExtraAttributes(
                                  equalTo(NETWORK_PEER_ADDRESS, ip),
                                  equalTo(NETWORK_PEER_PORT, containerConnection.port),
                                  equalTo(SERVER_ADDRESS, host),
                                  equalTo(SERVER_PORT, containerConnection.port),
                                  equalTo(DB_SYSTEM_NAME, REDIS),
                                  equalTo(DB_QUERY_TEXT, "SHUTDOWN NOSAVE"),
                                  equalTo(DB_OPERATION_NAME, "SHUTDOWN"),
                                  equalTo(
                                      ERROR_TYPE,
                                      testLatestDeps()
                                          ? RedisException.class.getName()
                                          : "_OTHER")));
                      if (testLatestDeps()) {
                        // Lettuce 6+ also exposes the exception.
                        span.hasException(new RedisException("Connection disconnected"))
                            .hasEventsSatisfyingExactly(event -> event.hasName("exception"));
                      }
                    }));

    assertCommandMetric("SHUTDOWN", testLatestDeps() ? RedisException.class.getName() : "_OTHER");
  }
}
