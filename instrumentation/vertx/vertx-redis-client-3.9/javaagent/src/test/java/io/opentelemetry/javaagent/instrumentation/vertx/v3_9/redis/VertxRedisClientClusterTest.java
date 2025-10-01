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
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_STATEMENT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.vertx.core.Vertx;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.RedisAPI;
import io.vertx.redis.client.RedisClientType;
import io.vertx.redis.client.RedisOptions;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.GenericContainer;

class VertxRedisClientClusterTest {
  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  // For cluster testing, we'll simulate with multiple standalone Redis instances
  // In a real cluster test, you'd use Redis Cluster configuration
  private static final GenericContainer<?> redisNode1 =
      new GenericContainer<>("redis:6.2.3-alpine").withExposedPorts(6379);
  private static final GenericContainer<?> redisNode2 =
      new GenericContainer<>("redis:6.2.3-alpine").withExposedPorts(6379);

  private static String host1;
  private static int port1;
  private static String host2;
  private static int port2;
  private static Vertx vertx;
  private static Redis redis;
  private static RedisAPI client;

  @BeforeAll
  static void setup() throws Exception {
    // Start multiple Redis instances to simulate cluster endpoints
    redisNode1.start();
    redisNode2.start();

    host1 = redisNode1.getHost();
    port1 = redisNode1.getMappedPort(6379);
    host2 = redisNode2.getHost();
    port2 = redisNode2.getMappedPort(6379);

    vertx = Vertx.vertx();

    // Create Redis client with multiple endpoints (simulating cluster mode)
    RedisOptions config =
        new RedisOptions()
            .setType(
                RedisClientType
                    .STANDALONE) // For testing, we use standalone but with multiple endpoints
            .setEndpoints(
                Arrays.asList("redis://" + host1 + ":" + port1, "redis://" + host2 + ":" + port2));

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
    redisNode1.stop();
    redisNode2.stop();
  }

  @Test
  void testClusterModeSetCommand() throws Exception {
    CompletableFuture<String> future = new CompletableFuture<>();

    client.set(
        Arrays.asList("cluster-key", "cluster-value"),
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
                            clusterSpanAttributes("SET", "SET cluster-key ?"))));

    if (emitStableDatabaseSemconv()) {
      testing.waitAndAssertMetrics(
          "io.opentelemetry.vertx-redis-client-3.9",
          metric -> metric.hasName("db.client.operation.duration"));
    }
  }

  @Test
  void testClusterModeGetCommand() throws Exception {
    // First set a value
    CompletableFuture<String> setFuture = new CompletableFuture<>();
    client.set(
        Arrays.asList("cluster-get-key", "cluster-get-value"),
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
        "cluster-get-key",
        result -> {
          if (result.succeeded()) {
            getFuture.complete(result.result().toString());
          } else {
            getFuture.completeExceptionally(result.cause());
          }
        });

    String response = getFuture.get(30, TimeUnit.SECONDS);
    assertThat(response).isEqualTo("cluster-get-value");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("GET")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            clusterSpanAttributes("GET", "GET cluster-get-key"))));
  }

  @SuppressWarnings("deprecation") // using deprecated semconv
  private static AttributeAssertion[] clusterSpanAttributes(String operation, String statement) {
    if (emitStableDatabaseSemconv()) {
      return new AttributeAssertion[] {
        equalTo(DB_SYSTEM_NAME, "redis"),
        equalTo(DB_QUERY_TEXT, statement),
        equalTo(DB_OPERATION_NAME, operation),
        // For cluster mode, we expect one of the endpoints
        // SERVER_ADDRESS and SERVER_PORT will be one of our nodes
        // NETWORK_PEER_PORT will match one of our ports
        equalTo(NETWORK_PEER_PORT, (long) port1) // Will connect to first endpoint typically
      };
    } else {
      return new AttributeAssertion[] {
        equalTo(DB_SYSTEM, "redis"),
        equalTo(DB_STATEMENT, statement),
        equalTo(DB_OPERATION, operation),
        equalTo(NETWORK_PEER_PORT, (long) port1)
      };
    }
  }
}
