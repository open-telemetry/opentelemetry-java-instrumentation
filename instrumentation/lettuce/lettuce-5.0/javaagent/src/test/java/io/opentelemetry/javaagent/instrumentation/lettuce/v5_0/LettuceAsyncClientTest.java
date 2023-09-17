/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchException;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.ConnectionFuture;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.codec.Utf8StringCodec;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.semconv.SemanticAttributes;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.assertj.core.api.AbstractAssert;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

@SuppressWarnings("deprecation") // until old http semconv are dropped in 2.0
class LettuceAsyncClientTest {
  @RegisterExtension
  protected static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  private static final int DB_INDEX = 0;

  // Disable autoreconnect so we do not get stray traces popping up on server shutdown
  private static final ClientOptions CLIENT_OPTIONS =
      ClientOptions.builder().autoReconnect(false).build();

  static final DockerImageName containerImage = DockerImageName.parse("redis:6.2.3-alpine");

  private static final GenericContainer<?> redisServer =
      new GenericContainer<>(containerImage).withExposedPorts(6379);

  private static String host;
  private static int port;
  private static int incorrectPort;
  private static String dbUriNonExistent;
  private static String embeddedDbUri;

  //  private static final ImmutableMap<String, String> testHashMap =
  //      ImmutableMap.of(
  //          "firstname", "John",
  //          "lastname", "Doe",
  //          "age", "53");

  static RedisClient redisClient;

  private static StatefulRedisConnection<String, String> connection;
  //  static RedisCommands<String, String> syncCommands;
  static RedisAsyncCommands<String, String> asyncCommands;

  @BeforeAll
  static void setUp() {
    redisServer.start();
    host = redisServer.getHost();
    port = redisServer.getMappedPort(6379);
    embeddedDbUri = "redis://" + host + ":" + port + "/" + DB_INDEX;

    incorrectPort = PortUtils.findOpenPort();
    dbUriNonExistent = "redis://" + host + ":" + incorrectPort + "/" + DB_INDEX;

    redisClient = RedisClient.create(embeddedDbUri);
    redisClient.setOptions(CLIENT_OPTIONS);

    connection = redisClient.connect();
    asyncCommands = connection.async();
    RedisCommands<String, String> syncCommands = connection.sync();

    syncCommands.set("TESTKEY", "TESTVAL");

    // 1 set + 1 connect trace
    testing.clearData();
  }

  @AfterAll
  static void cleanUp() {
    connection.close();
    redisServer.stop();
  }

  @Test
  void testConnectUsingGetOnConnectionFuture() throws ExecutionException, InterruptedException {
    RedisClient testConnectionClient = RedisClient.create(embeddedDbUri);
    testConnectionClient.setOptions(CLIENT_OPTIONS);

    ConnectionFuture<StatefulRedisConnection<String, String>> connectionFuture =
        testConnectionClient.connectAsync(
            new Utf8StringCodec(), new RedisURI(host, port, 3, TimeUnit.SECONDS));
    StatefulRedisConnection<String, String> connection1 = connectionFuture.get();
    cleanup.deferCleanup(connection1);

    assertThat(connection1).isNotNull();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("CONNECT")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.NET_PEER_NAME, host),
                            equalTo(SemanticAttributes.NET_PEER_PORT, port),
                            equalTo(SemanticAttributes.DB_SYSTEM, "redis"))));
  }

  @Test
  void testExceptionInsideTheConnectionFuture() {
    RedisClient testConnectionClient = RedisClient.create(dbUriNonExistent);
    testConnectionClient.setOptions(CLIENT_OPTIONS);

    Exception exception =
        catchException(
            () -> {
              ConnectionFuture<StatefulRedisConnection<String, String>> connectionFuture =
                  testConnectionClient.connectAsync(
                      new Utf8StringCodec(),
                      new RedisURI(host, incorrectPort, 3, TimeUnit.SECONDS));
              connectionFuture.get();
            });

    assertThat(exception).isInstanceOf(ExecutionException.class);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("CONNECT")
                        .hasKind(SpanKind.CLIENT)
                        .hasStatus(StatusData.error())
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.NET_PEER_NAME, host),
                            equalTo(SemanticAttributes.NET_PEER_PORT, incorrectPort),
                            equalTo(SemanticAttributes.DB_SYSTEM, "redis"))
                        .hasEventsSatisfyingExactly(
                            event ->
                                event
                                    .hasName("exception")
                                    .hasAttributesSatisfyingExactly(
                                        equalTo(
                                            AttributeKey.stringKey("exception.type"),
                                            "io.netty.channel.AbstractChannel.AnnotatedConnectException"),
                                        equalTo(
                                            AttributeKey.stringKey("exception.message"),
                                            "Connection refused: localhost/127.0.0.1:"
                                                + incorrectPort),
                                        satisfies(
                                            AttributeKey.stringKey("exception.stacktrace"),
                                            AbstractAssert::isNotNull)))));
  }
}
