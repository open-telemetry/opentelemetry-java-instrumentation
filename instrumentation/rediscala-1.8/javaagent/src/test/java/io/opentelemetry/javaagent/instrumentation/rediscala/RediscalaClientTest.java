/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rediscala;

import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.shaded.org.apache.commons.lang3.tuple.Pair;
import redis.ByteStringDeserializer;
import redis.ByteStringDeserializer$;
import redis.ByteStringSerializer;
import redis.ByteStringSerializer$;
import redis.RedisClient;
import redis.RedisDispatcher;
import scala.Option;
import scala.concurrent.Future;

class RediscalaClientTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  private static GenericContainer<?> redisServer;

  @SuppressWarnings("deprecation") // DB_OPERATION is  deprecated
  public static final AttributeKey<String> DB_OPERATION = DbIncubatingAttributes.DB_OPERATION;

  private static Object system;
  private static RedisClient redisClient;

  @BeforeAll
  static void setUp() throws Exception {
    redisServer = new GenericContainer<>("redis:6.2.3-alpine").withExposedPorts(6379);
    redisServer.start();

    String host = redisServer.getHost();
    Integer port = redisServer.getMappedPort(6379);

    try {
      Class<?> clazz = Class.forName("akka.actor.ActorSystem");
      system = clazz.getMethod("create").invoke(null);
    } catch (ClassNotFoundException exception) {
      Class<?> clazz = Class.forName("org.apache.pekko.actor.ActorSystem");
      system = clazz.getMethod("create").invoke(null);
    }

    try {
      RedisClient.class.getMethod("username");
      redisClient =
          (RedisClient)
              RedisClient.class.getConstructors()[0].newInstance(
                  host,
                  port,
                  Option.apply(null),
                  Option.apply(null),
                  Option.apply(null),
                  "RedisClient",
                  Option.apply(null),
                  system,
                  new RedisDispatcher("rediscala.rediscala-client-worker-dispatcher"));
    } catch (Exception e) {
      redisClient =
          (RedisClient)
              RedisClient.class.getConstructors()[0].newInstance(
                  host,
                  port,
                  Option.apply(null),
                  Option.apply(null),
                  "RedisClient",
                  Option.apply(null),
                  system,
                  new RedisDispatcher("rediscala.rediscala-client-worker-dispatcher"));
    }
  }

  @AfterAll
  static void tearDown() throws Exception {
    redisServer.stop();
    if (system != null) {
      system.getClass().getMethod("terminate").invoke(system);
    }
  }

  @Test
  void testGetCommand() {
    Pair<Future<Object>, Future<Option<String>>> result =
        testing.runWithSpan(
            "parent",
            () -> {
              ByteStringSerializer<String> serializer = ByteStringSerializer$.MODULE$.String();
              ByteStringDeserializer<String> deserializer =
                  ByteStringDeserializer$.MODULE$.String();
              Future<Object> writeFuture =
                  redisClient.set(
                      "bar",
                      "baz",
                      Option.apply(null),
                      Option.apply(null),
                      false,
                      false,
                      serializer);
              Future<Option<String>> valueFuture = redisClient.get("bar", deserializer);
              return Pair.of(writeFuture, valueFuture);
            });

    await().atMost(java.time.Duration.ofSeconds(3)).until(() -> result.getLeft().isCompleted());
    await().atMost(java.time.Duration.ofSeconds(3)).until(() -> result.getRight().isCompleted());
    assertThat(result.getLeft().value().get().get()).isEqualTo(true);
    assertThat(result.getRight().value().get().get().get()).isEqualTo("baz");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent(),
                span ->
                    span.hasName("SET")
                        .hasKind(CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(DbIncubatingAttributes.DB_SYSTEM, "redis"),
                            equalTo(DB_OPERATION, "SET")),
                span ->
                    span.hasName("GET")
                        .hasKind(CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(DbIncubatingAttributes.DB_SYSTEM, "redis"),
                            equalTo(DB_OPERATION, "GET"))));
  }

  @Test
  public void testSetCommand() {
    ByteStringSerializer<String> serializer = ByteStringSerializer$.MODULE$.String();

    Future<Object> value =
        testing.runWithSpan(
            "parent",
            () ->
                redisClient.set(
                    "foo",
                    "bar",
                    Option.apply(null),
                    Option.apply(null),
                    false,
                    false,
                    serializer));
    await().atMost(java.time.Duration.ofSeconds(3)).until(value::isCompleted);
    assertThat(value.value().get().get()).isEqualTo(true);
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent(),
                span ->
                    span.hasName("SET")
                        .hasKind(CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfying(
                            equalTo(DbIncubatingAttributes.DB_SYSTEM, "redis"),
                            equalTo(DB_OPERATION, "SET"))));
  }
}
