/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.v4_0.redis;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.semconv.NetworkAttributes;
import io.opentelemetry.semconv.ServerAttributes;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes;
import io.vertx.core.Vertx;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.RedisAPI;
import io.vertx.redis.client.RedisConnection;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.GenericContainer;

class VertxRedisClientTest {
  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static final GenericContainer<?> redisServer =
      new GenericContainer<>("redis:6.2.3-alpine").withExposedPorts(6379);
  private static String host;
  private static String ip;
  private static int port;
  private static Vertx vertx;
  private static Redis client;
  private static RedisAPI redis;

  @BeforeAll
  static void setup() throws Exception {
    redisServer.start();

    host = redisServer.getHost();
    ip = InetAddress.getByName(host).getHostAddress();
    port = redisServer.getMappedPort(6379);

    vertx = Vertx.vertx();
    client = Redis.createClient(vertx, "redis://" + host + ":" + port + "/1");
    RedisConnection connection =
        client.connect().toCompletionStage().toCompletableFuture().get(30, TimeUnit.SECONDS);
    redis = RedisAPI.api(connection);
  }

  @AfterAll
  static void cleanup() {
    redis.close();
    client.close();
    redisServer.stop();
  }

  @Test
  void setCommand() throws Exception {
    redis
        .set(Arrays.asList("foo", "bar"))
        .toCompletionStage()
        .toCompletableFuture()
        .get(30, TimeUnit.SECONDS);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("SET")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(redisSpanAttributes("SET", "SET foo ?"))));
  }

  @Test
  void getCommand() throws Exception {
    redis
        .set(Arrays.asList("foo", "bar"))
        .toCompletionStage()
        .toCompletableFuture()
        .get(30, TimeUnit.SECONDS);
    String value =
        redis
            .get("foo")
            .toCompletionStage()
            .toCompletableFuture()
            .get(30, TimeUnit.SECONDS)
            .toString();

    assertThat(value).isEqualTo("bar");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("SET")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(redisSpanAttributes("SET", "SET foo ?"))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("GET")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(redisSpanAttributes("GET", "GET foo"))));
  }

  @Test
  void getCommandWithParent() throws Exception {
    redis
        .set(Arrays.asList("foo", "bar"))
        .toCompletionStage()
        .toCompletableFuture()
        .get(30, TimeUnit.SECONDS);

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

    String value = result.get(30, TimeUnit.SECONDS);
    assertThat(value).isEqualTo("bar");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("SET")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(redisSpanAttributes("SET", "SET foo ?"))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL),
                span ->
                    span.hasName("GET")
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
    redis
        .set(Arrays.asList("foo", "bar"))
        .toCompletionStage()
        .toCompletableFuture()
        .get(30, TimeUnit.SECONDS);

    String value =
        redis
            .randomkey()
            .toCompletionStage()
            .toCompletableFuture()
            .get(30, TimeUnit.SECONDS)
            .toString();

    assertThat(value).isEqualTo("foo");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("SET")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(redisSpanAttributes("SET", "SET foo ?"))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("RANDOMKEY")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            redisSpanAttributes("RANDOMKEY", "RANDOMKEY"))));
  }

  private static AttributeAssertion[] redisSpanAttributes(String operation, String statement) {
    return new AttributeAssertion[] {
      equalTo(DbIncubatingAttributes.DB_SYSTEM, "redis"),
      equalTo(DbIncubatingAttributes.DB_STATEMENT, statement),
      equalTo(DbIncubatingAttributes.DB_OPERATION, operation),
      equalTo(DbIncubatingAttributes.DB_REDIS_DATABASE_INDEX, 1),
      equalTo(ServerAttributes.SERVER_ADDRESS, host),
      equalTo(ServerAttributes.SERVER_PORT, port),
      equalTo(NetworkAttributes.NETWORK_PEER_PORT, port),
      equalTo(NetworkAttributes.NETWORK_PEER_ADDRESS, ip)
    };
  }
}
