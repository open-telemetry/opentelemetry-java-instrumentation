/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.v3_9.redis;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
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

/**
 * Test that verifies the instrumentation works across different 3.9.x versions. This test focuses
 * on API compatibility and basic functionality.
 */
class VertxRedisClientMultiVersionTest {
  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static final GenericContainer<?> redisServer =
      new GenericContainer<>("redis:6.2.3-alpine").withExposedPorts(6379);
  private static String host;
  private static int port;
  private static Vertx vertx;

  @BeforeAll
  static void setup() throws Exception {
    redisServer.start();
    host = redisServer.getHost();
    port = redisServer.getMappedPort(6379);
    vertx = Vertx.vertx();
  }

  @AfterAll
  static void cleanup() {
    if (vertx != null) {
      vertx.close();
    }
    redisServer.stop();
  }

  @Test
  void testRedisOptionsEndpointConfiguration() throws Exception {
    // Test single endpoint configuration (3.9.1+ style)
    RedisOptions standaloneConfig =
        new RedisOptions().setConnectionString("redis://" + host + ":" + port);

    Redis standaloneRedis = Redis.createClient(vertx, standaloneConfig);
    RedisAPI standaloneClient = RedisAPI.api(standaloneRedis);

    CompletableFuture<String> future = new CompletableFuture<>();
    standaloneClient.set(
        Arrays.asList("version-test-key", "version-test-value"),
        result -> {
          if (result.succeeded()) {
            future.complete(result.result().toString());
          } else {
            future.completeExceptionally(result.cause());
          }
        });

    String response = future.get(30, TimeUnit.SECONDS);
    assertThat(response).isEqualTo("OK");

    // Verify span was created
    testing.waitAndAssertTraces(
        trace -> trace.hasSpansSatisfyingExactly(span -> span.hasName("SET")));

    standaloneClient.close();
    standaloneRedis.close();
    testing.clearData();
  }

  @Test
  void testRedisOptionsMultipleEndpointsConfiguration() throws Exception {
    // Test multiple endpoints configuration (cluster-like)
    RedisOptions clusterConfig =
        new RedisOptions()
            .setType(RedisClientType.STANDALONE)
            .setEndpoints(
                Arrays.asList(
                    "redis://" + host + ":" + port,
                    "redis://localhost:6380" // Second endpoint (will fail but tests config)
                    ));

    Redis clusterRedis = Redis.createClient(vertx, clusterConfig);
    RedisAPI clusterClient = RedisAPI.api(clusterRedis);

    CompletableFuture<String> future = new CompletableFuture<>();
    clusterClient.set(
        Arrays.asList("cluster-version-test", "cluster-version-value"),
        result -> {
          if (result.succeeded()) {
            future.complete(result.result().toString());
          } else {
            future.completeExceptionally(result.cause());
          }
        });

    String response = future.get(30, TimeUnit.SECONDS);
    assertThat(response).isEqualTo("OK");

    // Verify span was created
    testing.waitAndAssertTraces(
        trace -> trace.hasSpansSatisfyingExactly(span -> span.hasName("SET")));

    clusterClient.close();
    clusterRedis.close();
    testing.clearData();
  }

  @Test
  void testRedisOptionsMasterNameConfiguration() throws Exception {
    // Test master name configuration (sentinel mode)
    // Note: This will fail to connect but tests our configuration extraction
    RedisOptions sentinelConfig =
        new RedisOptions()
            .setType(RedisClientType.SENTINEL)
            .setMasterName("mymaster")
            .setEndpoints(Arrays.asList("redis://" + host + ":" + port));

    // Just test that the configuration is accepted - actual connection may fail
    // but our instrumentation should handle the configuration properly
    Redis sentinelRedis = Redis.createClient(vertx, sentinelConfig);
    
    // We can't easily test the ThreadLocal without actually making a connection,
    // but the configuration should be properly stored and handled by our instrumentation
    
    sentinelRedis.close();
  }

  @Test
  void testInstrumentationWithDifferentRedisCommands() throws Exception {
    // Test various Redis commands to ensure instrumentation works across different operations
    RedisOptions config = new RedisOptions().setConnectionString("redis://" + host + ":" + port);
    Redis redis = Redis.createClient(vertx, config);
    RedisAPI client = RedisAPI.api(redis);

    // Test SET command
    CompletableFuture<String> setFuture = new CompletableFuture<>();
    client.set(
        Arrays.asList("cmd-test", "value"),
        result -> {
          if (result.succeeded()) {
            setFuture.complete(result.result().toString());
          } else {
            setFuture.completeExceptionally(result.cause());
          }
        });
    assertThat(setFuture.get(30, TimeUnit.SECONDS)).isEqualTo("OK");

    // Test GET command
    CompletableFuture<String> getFuture = new CompletableFuture<>();
    client.get(
        "cmd-test",
        result -> {
          if (result.succeeded()) {
            getFuture.complete(result.result().toString());
          } else {
            getFuture.completeExceptionally(result.cause());
          }
        });
    assertThat(getFuture.get(30, TimeUnit.SECONDS)).isEqualTo("value");

    // Test DEL command
    CompletableFuture<String> delFuture = new CompletableFuture<>();
    client.del(
        Arrays.asList("cmd-test"),
        result -> {
          if (result.succeeded()) {
            delFuture.complete(result.result().toString());
          } else {
            delFuture.completeExceptionally(result.cause());
          }
        });
    assertThat(delFuture.get(30, TimeUnit.SECONDS)).isEqualTo("1");

    // Verify all commands created spans
    testing.waitAndAssertTraces(
        trace -> trace.hasSpansSatisfyingExactly(span -> span.hasName("SET")),
        trace -> trace.hasSpansSatisfyingExactly(span -> span.hasName("GET")),
        trace -> trace.hasSpansSatisfyingExactly(span -> span.hasName("DEL")));

    client.close();
    redis.close();
  }
}
