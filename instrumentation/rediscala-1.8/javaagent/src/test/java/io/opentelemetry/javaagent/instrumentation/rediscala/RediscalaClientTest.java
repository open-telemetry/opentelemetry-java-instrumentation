/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rediscala;

import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemIncubatingValues.REDIS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.google.common.util.concurrent.SettableFuture;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.GenericContainer;
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

  private static GenericContainer<?> redisServer;

  @SuppressWarnings("deprecation") // DB_OPERATION is  deprecated
  private static final AttributeKey<String> DB_OPERATION =
      SemconvStabilityUtil.maybeStable(DbIncubatingAttributes.DB_OPERATION);

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
  void testGetCommand() throws Exception {
    SettableFuture<Future<Object>> writeFutureRef = SettableFuture.create();
    ;
    SettableFuture<Future<Option<String>>> valueFutureRef = SettableFuture.create();

    testing.runWithSpan(
        "parent",
        () -> {
          ByteStringSerializer<String> serializer = ByteStringSerializer$.MODULE$.String();
          ByteStringDeserializer<String> deserializer = ByteStringDeserializer$.MODULE$.String();
          writeFutureRef.set(
              redisClient.set(
                  "bar", "baz", Option.apply(null), Option.apply(null), false, false, serializer));
          valueFutureRef.set(redisClient.get("bar", deserializer));
        });

    await().atMost(Duration.ofSeconds(3)).until(() -> writeFutureRef.get().isCompleted());
    await().atMost(Duration.ofSeconds(3)).until(() -> valueFutureRef.get().isCompleted());
    assertThat(writeFutureRef.get().value().get().get()).isEqualTo(true);
    assertThat(valueFutureRef.get().value().get().get().get()).isEqualTo("baz");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent(),
                span ->
                    span.hasName("SET")
                        .hasKind(CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM, REDIS), equalTo(DB_OPERATION, "SET")),
                span ->
                    span.hasName("GET")
                        .hasKind(CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM, REDIS), equalTo(DB_OPERATION, "GET"))));
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
    await().atMost(Duration.ofSeconds(3)).until(value::isCompleted);
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
                            equalTo(DB_SYSTEM, REDIS), equalTo(DB_OPERATION, "SET"))));
  }
}
