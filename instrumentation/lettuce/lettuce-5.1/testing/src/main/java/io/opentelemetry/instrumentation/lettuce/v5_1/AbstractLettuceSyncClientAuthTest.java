/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.lettuce.v5_1;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static org.assertj.core.api.Assertions.assertThat;

import io.lettuce.core.api.sync.RedisCommands;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.semconv.NetworkAttributes;
import io.opentelemetry.semconv.SemanticAttributes;
import io.opentelemetry.semconv.ServerAttributes;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public abstract class AbstractLettuceSyncClientAuthTest extends AbstractLettuceClientTest {

  @BeforeAll
  void setUp() {
    redisServer = redisServer.withCommand("redis-server", "--requirepass password");
    redisServer.start();

    host = redisServer.getHost();
    port = redisServer.getMappedPort(6379);
    embeddedDbUri = "redis://" + host + ":" + port + "/" + DB_INDEX;

    redisClient = createClient(embeddedDbUri);
    redisClient.setOptions(LettuceTestUtil.CLIENT_OPTIONS);
  }

  @AfterAll
  static void cleanUp() {
    redisClient.shutdown();
    redisServer.stop();

    // Set back so other tests don't fail due to NOAUTH error
    redisServer = redisServer.withCommand("redis-server", "--requirepass \"\"");
  }

  @Test
  void testAuthCommand() throws Exception {
    Class<?> commandsClass = RedisCommands.class;
    java.lang.reflect.Method authMethod;
    // the auth() argument type changed between 5.x -> 6.x
    try {
      authMethod = commandsClass.getMethod("auth", String.class);
    } catch (NoSuchMethodException unused) {
      authMethod = commandsClass.getMethod("auth", CharSequence.class);
    }

    String result = (String) authMethod.invoke(redisClient.connect().sync(), "password");

    assertThat(result).isEqualTo("OK");

    getInstrumentationExtension()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("AUTH")
                            .hasKind(SpanKind.CLIENT)
                            .hasAttributesSatisfyingExactly(
                                equalTo(NetworkAttributes.NETWORK_TYPE, "ipv4"),
                                equalTo(NetworkAttributes.NETWORK_PEER_ADDRESS, "127.0.0.1"),
                                equalTo(NetworkAttributes.NETWORK_PEER_PORT, port),
                                equalTo(ServerAttributes.SERVER_ADDRESS, "localhost"),
                                equalTo(ServerAttributes.SERVER_PORT, port),
                                equalTo(SemanticAttributes.DB_SYSTEM, "redis"),
                                equalTo(SemanticAttributes.DB_STATEMENT, "AUTH ?"))
                            .hasEventsSatisfyingExactly(
                                event -> event.hasName("redis.encode.start"),
                                event -> event.hasName("redis.encode.end"))));
  }
}
