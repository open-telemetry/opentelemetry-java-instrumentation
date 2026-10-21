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
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import com.google.common.collect.ImmutableMap;
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
import io.lettuce.core.codec.StringCodec;
import io.lettuce.core.output.MultiOutput;
import io.lettuce.core.output.StatusOutput;
import io.lettuce.core.protocol.Command;
import io.lettuce.core.protocol.CommandType;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.sdk.testing.assertj.HistogramPointAssert;
import io.opentelemetry.sdk.testing.assertj.TraceAssert;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

@SuppressWarnings("deprecation") // using deprecated semconv
public abstract class AbstractLettuceSyncClientTest extends AbstractLettuceClientTest {

  private String dbUriNonExistent;

  private static final ImmutableMap<String, String> TEST_HASH_MAP =
      ImmutableMap.of(
          "firstname", "John",
          "lastname", "Doe",
          "age", "53");

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
                                    equalTo(ERROR_TYPE, testLatestDeps() ? "WRONGTYPE" : "_OTHER")))
                            .satisfies(AbstractLettuceClientTest::assertCommandErrorEvents)));

    assertCommandMetric("LPUSH", testLatestDeps() ? "WRONGTYPE" : "_OTHER");
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
                                        ERROR_TYPE, testLatestDeps() ? "NOSCRIPT" : "_OTHER")))));

    assertCommandMetric("EVALSHA", testLatestDeps() ? "NOSCRIPT" : "_OTHER");
  }

  @ParameterizedTest
  @MethodSource("serverErrorReplies")
  void testServerErrorReply(String errorMessage, String errorType) {
    String lettuceVersion = RedisClient.class.getPackage().getImplementationVersion();
    // Lettuce 6.0.0 supplies no remote endpoint to command-aware spans.
    boolean missingEndpoint = "6.0.0.RELEASE".equals(lettuceVersion);
    String script = "return redis.error_reply(ARGV[1])";
    Throwable thrown =
        catchThrowable(
            () -> syncCommands.eval(script, ScriptOutputType.VALUE, new String[0], errorMessage));
    StatusOutput<String, String> uninstrumentedOutput = new StatusOutput<>(StringCodec.UTF8);
    uninstrumentedOutput.setError(ByteBuffer.wrap(errorMessage.getBytes(UTF_8)));
    assertThat(thrown)
        .isInstanceOf(RedisCommandExecutionException.class)
        .hasMessage(uninstrumentedOutput.getError());

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
                                        ERROR_TYPE, testLatestDeps() ? errorType : "_OTHER")))));

    assertCommandMetric("EVAL", testLatestDeps() ? errorType : "_OTHER");
  }

  private static Stream<Arguments> serverErrorReplies() {
    return Stream.of(
        argumentSet(
            "timeout-looking details",
            "ERR upstream failed. Command timed out after 100 millisecond(s)",
            "ERR"),
        argumentSet(
            "timeout-looking prefix", "Command timed out after 100 millisecond(s)", "Command"),
        argumentSet("disconnect-looking reply", "Connection closed", "Connection"),
        argumentSet("lowercase", "wrongtype details", "wrongtype"),
        argumentSet("punctuation", "Custom.Code! details", "Custom.Code!"),
        argumentSet("leading spaces", "   Custom.Code! details", "Custom.Code!"),
        argumentSet("tab within prefix", "ERR\tDETAIL message", "ERR\tDETAIL"),
        argumentSet("non-ASCII prefix", "échec details", "échec"),
        argumentSet("CJK prefix", "错误.Code! details", "错误.Code!"),
        argumentSet("literal decoder lookalike", "ￃﾩ details", "ￃﾩ"),
        argumentSet(
            "long prefix",
            "ABCDEFGHIJKLMNOPQRSTUVWXYZABCDEFGHIJKLMNOPQRSTUVWXYZABCDEFGHIJKLMN details",
            "ABCDEFGHIJKLMNOPQRSTUVWXYZABCDEFGHIJKLMNOPQRSTUVWXYZABCDEFGHIJKLMN"),
        argumentSet("empty reply", "", RedisCommandExecutionException.class.getName()),
        argumentSet("spaces only", "   ", RedisCommandExecutionException.class.getName()));
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"échec", "错误.Code!", "ￃﾩ"})
  void testTransactionResults(@Nullable String prefix)
      throws ExecutionException, InterruptedException, TimeoutException {
    String lettuceVersion = RedisClient.class.getPackage().getImplementationVersion();
    boolean missingEndpoint = "6.0.0.RELEASE".equals(lettuceVersion);
    boolean queuedAcknowledgements = !testLatestDeps() || missingEndpoint;
    String script = prefix != null ? "return redis.error_reply(ARGV[1])" : "return ARGV[1]";
    assertThat(syncCommands.multi()).isEqualTo("OK");
    RedisFuture<String> successful = connection.async().set("transaction-key", "value");
    RedisFuture<Object> evaluated =
        connection
            .async()
            .eval(
                script,
                ScriptOutputType.VALUE,
                new String[0],
                prefix != null ? prefix + " details" : "value");
    RedisFuture<?> last =
        prefix != null
            ? connection.async().evalsha("00000000", ScriptOutputType.VALUE)
            : connection.async().get("transaction-key");

    testing().waitForTraces(queuedAcknowledgements ? 4 : 1);
    assertThat(successful.toCompletableFuture()).isNotDone();
    assertThat(evaluated.toCompletableFuture()).isNotDone();
    assertThat(last.toCompletableFuture()).isNotDone();

    TransactionResult transaction = syncCommands.exec();
    assertThat(transaction).hasSize(3);
    assertThat(transaction.<String>get(0)).isEqualTo("OK");
    assertThat(successful.get(10, SECONDS)).isEqualTo("OK");
    if (prefix != null) {
      StatusOutput<String, String> uninstrumentedOutput = new StatusOutput<>(StringCodec.UTF8);
      MultiOutput<String, String> uninstrumentedMulti = new MultiOutput<>(StringCodec.UTF8);
      uninstrumentedMulti.add(new Command<>(CommandType.EVAL, uninstrumentedOutput));
      uninstrumentedMulti.multi(1);
      uninstrumentedMulti.setError(ByteBuffer.wrap((prefix + " details").getBytes(UTF_8)));
      assertThat(transaction.<Throwable>get(1))
          .isInstanceOf(RedisCommandExecutionException.class)
          .hasMessage(uninstrumentedOutput.getError());
      assertThat(transaction.<Throwable>get(2)).isInstanceOf(RedisNoScriptException.class);
      assertThatThrownBy(() -> evaluated.get(10, SECONDS))
          .hasCauseInstanceOf(RedisCommandExecutionException.class)
          .cause()
          .hasMessage(uninstrumentedOutput.getError());
      assertThatThrownBy(() -> last.get(10, SECONDS))
          .hasCauseInstanceOf(RedisNoScriptException.class);
    } else {
      assertThat(transaction.<String>get(1)).isEqualTo("value");
      assertThat(transaction.<String>get(2)).isEqualTo("value");
      assertThat(evaluated.get(10, SECONDS)).isEqualTo("value");
      assertThat(last.get(10, SECONDS)).isEqualTo("value");
    }

    List<String> operations =
        asList("MULTI", "SET", "EVAL", prefix != null ? "EVALSHA" : "GET", "EXEC");
    List<String> statements =
        asList(
            "MULTI",
            "SET transaction-key ?",
            "EVAL " + Base64.getEncoder().encodeToString(script.getBytes(UTF_8)) + " 0 ?",
            prefix != null ? "EVALSHA 00000000 0" : "GET transaction-key",
            "EXEC");
    testing()
        .waitAndAssertTraces(
            IntStream.range(0, operations.size())
                .<Consumer<TraceAssert>>mapToObj(
                    i ->
                        trace ->
                            trace.hasSpansSatisfyingExactly(
                                span ->
                                    span.hasName(
                                            missingEndpoint
                                                ? operations.get(i)
                                                : spanName(operations.get(i)))
                                        .hasKind(SpanKind.CLIENT)
                                        .hasNoParent()
                                        .hasStatus(
                                            testLatestDeps()
                                                    && prefix != null
                                                    && (i == 4
                                                        || (!queuedAcknowledgements && i > 1))
                                                ? StatusData.error()
                                                : StatusData.unset())
                                        .hasAttributesSatisfyingExactly(
                                            equalTo(DB_SYSTEM_NAME, REDIS),
                                            equalTo(DB_QUERY_TEXT, statements.get(i)),
                                            equalTo(DB_OPERATION_NAME, operations.get(i)),
                                            equalTo(
                                                NETWORK_PEER_ADDRESS, missingEndpoint ? null : ip),
                                            equalTo(
                                                NETWORK_PEER_PORT,
                                                missingEndpoint ? null : (long) port),
                                            equalTo(SERVER_ADDRESS, missingEndpoint ? null : host),
                                            equalTo(
                                                SERVER_PORT, missingEndpoint ? null : (long) port),
                                            equalTo(
                                                DB_NAMESPACE,
                                                lettuceVersion != null
                                                        && lettuceVersion.startsWith(
                                                            "6.5.0.RELEASE")
                                                    ? String.valueOf(DB_INDEX)
                                                    : null),
                                            equalTo(
                                                ERROR_TYPE,
                                                testLatestDeps()
                                                        && (i == 4
                                                            || (i == 2 && !queuedAcknowledgements))
                                                    ? prefix
                                                    : testLatestDeps()
                                                            && i == 3
                                                            && !queuedAcknowledgements
                                                            && prefix != null
                                                        ? "NOSCRIPT"
                                                        : null))))
                .collect(toList()));

    testing()
        .waitAndAssertMetrics(
            "io.opentelemetry.lettuce-5.1",
            "db.client.operation.duration",
            metrics ->
                metrics.anySatisfy(
                    metric ->
                        assertThat(metric)
                            .hasHistogramSatisfying(
                                histogram ->
                                    histogram.hasPointsSatisfying(
                                        IntStream.range(0, operations.size())
                                            .<Consumer<HistogramPointAssert>>mapToObj(
                                                i ->
                                                    point ->
                                                        point
                                                            .hasCount(1)
                                                            .hasAttributesSatisfying(
                                                                equalTo(
                                                                    DB_OPERATION_NAME,
                                                                    operations.get(i)),
                                                                equalTo(
                                                                    ERROR_TYPE,
                                                                    testLatestDeps()
                                                                            && (i == 4
                                                                                || (i == 2
                                                                                    && !queuedAcknowledgements))
                                                                        ? prefix
                                                                        : testLatestDeps()
                                                                                && i == 3
                                                                                && !queuedAcknowledgements
                                                                                && prefix != null
                                                                            ? "NOSCRIPT"
                                                                            : null)))
                                            .collect(toList())))));
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
    String result = syncCommands.mset(ImmutableMap.of("key1", "value1", "key2", "value2"));

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
                                      equalTo(ERROR_TYPE, "ERR")))),
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
                                      equalTo(ERROR_TYPE, "ERR")))),
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
                                      equalTo(ERROR_TYPE, "ERR")))),
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
                        // Seems to only be treated as an error with Lettuce 6+
                        // and also produces an exception event
                        span.hasException(new RedisException("Connection disconnected"))
                            .hasEventsSatisfyingExactly(event -> event.hasName("exception"));
                      }
                    }));

    assertCommandMetric("SHUTDOWN", testLatestDeps() ? RedisException.class.getName() : "_OTHER");
  }
}
