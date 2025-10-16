/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.v3_9.redis;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_NAME;
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_TEXT;
import static io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_STATEMENT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.vertx.core.Vertx;
import io.vertx.redis.client.Command;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.RedisAPI;
import io.vertx.redis.client.RedisConnection;
import io.vertx.redis.client.RedisOptions;
import io.vertx.redis.client.Request;
import java.net.InetAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.GenericContainer;

class VertxRedisClientStandaloneTest {
  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static final GenericContainer<?> redisServer =
      new GenericContainer<>("redis:6.2.3-alpine").withExposedPorts(6379);
  private static String host;
  private static String ip;
  private static int port;
  private static Vertx vertx;
  private static Redis redis;
  private static RedisAPI client;

  @BeforeAll
  static void setup() throws Exception {
    redisServer.start();

    host = redisServer.getHost();
    ip = InetAddress.getByName(host).getHostAddress();
    port = redisServer.getMappedPort(6379);

    vertx = Vertx.vertx();
    RedisOptions config = new RedisOptions().setConnectionString("redis://" + host + ":" + port);
    redis = Redis.createClient(vertx, config);
    client = RedisAPI.api(redis);
  }

  @AfterAll
  static void cleanup() {
    if (client != null) {
      client.close();
    }
    if (redis != null) {
      redis.close();
    }
    if (vertx != null) {
      vertx.close();
    }
    redisServer.stop();
  }

  @Test
  void testStandaloneSetCommand() throws Exception {
    CompletableFuture<String> future = new CompletableFuture<>();

    client.set(
        java.util.Arrays.asList("test-key", "test-value"),
        result -> {
          if (result.succeeded()) {
            future.complete(result.result().toString());
          } else {
            future.completeExceptionally(result.cause());
          }
        });

    String response = future.get(30, TimeUnit.SECONDS);
    assertThat(response).isEqualTo("OK");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("SET")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            redisSpanAttributes("SET", "SET test-key ?"))));

    if (emitStableDatabaseSemconv()) {
      testing.waitAndAssertMetrics(
          "io.opentelemetry.vertx-redis-client-3.9",
          metric -> metric.hasName("db.client.operation.duration"));
    }
  }

  @Test
  void testStandaloneGetCommand() throws Exception {
    // First set a value
    CompletableFuture<String> setFuture = new CompletableFuture<>();
    client.set(
        java.util.Arrays.asList("get-test-key", "get-test-value"),
        result -> {
          if (result.succeeded()) {
            setFuture.complete(result.result().toString());
          } else {
            setFuture.completeExceptionally(result.cause());
          }
        });
    setFuture.get(30, TimeUnit.SECONDS);

    testing.clearData();

    // Now get the value
    CompletableFuture<String> getFuture = new CompletableFuture<>();
    client.get(
        "get-test-key",
        result -> {
          if (result.succeeded()) {
            getFuture.complete(result.result().toString());
          } else {
            getFuture.completeExceptionally(result.cause());
          }
        });

    String response = getFuture.get(30, TimeUnit.SECONDS);
    assertThat(response).isEqualTo("get-test-value");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("GET")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            redisSpanAttributes("GET", "GET get-test-key"))));
  }

  @Test
  void testDirectConnectionSend() throws Exception {
    // Test direct connection.send() method to ensure our instrumentation works
    CompletableFuture<RedisConnection> connectionFuture = new CompletableFuture<>();
    redis.connect(
        result -> {
          if (result.succeeded()) {
            connectionFuture.complete(result.result());
          } else {
            connectionFuture.completeExceptionally(result.cause());
          }
        });

    RedisConnection connection = connectionFuture.get(30, TimeUnit.SECONDS);

    CompletableFuture<String> commandFuture = new CompletableFuture<>();
    Request request = Request.cmd(Command.SET).arg("direct-key").arg("direct-value");

    connection.send(
        request,
        result -> {
          if (result.succeeded()) {
            commandFuture.complete(result.result().toString());
          } else {
            commandFuture.completeExceptionally(result.cause());
          }
        });

    String response = commandFuture.get(30, TimeUnit.SECONDS);
    assertThat(response).isEqualTo("OK");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("SET")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            redisSpanAttributes("SET", "SET direct-key ?"))));

    connection.close();
  }

  @SuppressWarnings("deprecation") // using deprecated semconv
  private static AttributeAssertion[] redisSpanAttributes(String operation, String statement) {
    if (emitStableDatabaseSemconv()) {
      return new AttributeAssertion[] {
        equalTo(DB_SYSTEM_NAME, "redis"),
        equalTo(DB_QUERY_TEXT, statement),
        equalTo(DB_OPERATION_NAME, operation),
        equalTo(SERVER_ADDRESS, host),
        equalTo(SERVER_PORT, port),
        equalTo(NETWORK_PEER_PORT, port),
        equalTo(NETWORK_PEER_ADDRESS, ip)
      };
    } else {
      return new AttributeAssertion[] {
        equalTo(DB_SYSTEM, "redis"),
        equalTo(DB_STATEMENT, statement),
        equalTo(DB_OPERATION, operation),
        equalTo(SERVER_ADDRESS, host),
        equalTo(SERVER_PORT, port),
        equalTo(NETWORK_PEER_PORT, port),
        equalTo(NETWORK_PEER_ADDRESS, ip)
      };
    }
  }
}
