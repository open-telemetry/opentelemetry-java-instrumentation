/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.redisclient.v4_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.vertx.core.Vertx;
import io.vertx.redis.client.Command;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.RedisClientType;
import io.vertx.redis.client.RedisConnection;
import io.vertx.redis.client.RedisOptions;
import io.vertx.redis.client.RedisRole;
import io.vertx.redis.client.Request;
import java.net.InetAddress;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.GenericContainer;

class VertxRedisClient403Test {
  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension
  private static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  private static final GenericContainer<?> redisServer =
      new GenericContainer<>("redis:6.2.3-alpine").withExposedPorts(6379);
  private static String host;
  private static String ip;
  private static int port;
  private static Vertx vertx;

  @BeforeAll
  static void setup() throws Exception {
    redisServer.start();
    cleanup.deferAfterAll(redisServer::stop);
    host = redisServer.getHost();
    ip = InetAddress.getByName(host).getHostAddress();
    port = redisServer.getMappedPort(6379);
    vertx = Vertx.vertx();
    cleanup.deferAfterAll(vertx::close);
  }

  @Test
  void capturesTargetFromProviderOptions() {
    Redis client =
        Redis.createClient(
            vertx,
            new RedisOptions()
                .setType(RedisClientType.SENTINEL)
                .setMasterName("themaster")
                .setRole(RedisRole.MASTER)
                .setConnectionString("redis://" + host + ":" + port));
    cleanup.deferCleanup(client::close);

    assertThatThrownBy(
            () -> client.connect().toCompletionStage().toCompletableFuture().get(30, SECONDS))
        .isInstanceOf(ExecutionException.class);

    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              List<SpanData> spans =
                  testing.spans().stream()
                      .filter(span -> span.getName().contains("SENTINEL"))
                      .collect(toList());
              assertThat(spans).isNotEmpty();
              for (SpanData span : spans) {
                assertThat(span.getAttributes().get(SERVER_ADDRESS))
                    .isEqualTo(
                        emitStableDatabaseSemconv() ? host + ":" + port + "/themaster" : host);
                assertThat(span.getAttributes().get(SERVER_PORT))
                    .isEqualTo(emitStableDatabaseSemconv() ? null : Long.valueOf(port));
                assertThat(span.getAttributes().get(NETWORK_PEER_ADDRESS)).isEqualTo(ip);
                assertThat(span.getAttributes().get(NETWORK_PEER_PORT))
                    .isEqualTo(Long.valueOf(port));
              }
            });
  }

  @Test
  void optionsReuseDoesNotChangeClientTarget() {
    RedisOptions options = new RedisOptions().setConnectionString("redis://" + host + ":" + port);
    Redis client = Redis.createClient(vertx, options);
    cleanup.deferCleanup(client::close);

    options.setConnectionString("redis://other:1234");
    Redis otherClient = Redis.createClient(vertx, options);
    cleanup.deferCleanup(otherClient::close);

    client
        .send(Request.cmd(Command.SET).arg("options-reuse").arg("value"))
        .toCompletionStage()
        .toCompletableFuture()
        .join();

    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              List<SpanData> spans =
                  testing.spans().stream()
                      .filter(span -> span.getName().startsWith("SET"))
                      .collect(toList());
              assertThat(spans).hasSize(1);
              assertThat(spans.get(0).getAttributes().get(SERVER_ADDRESS)).isEqualTo(host);
              assertThat(spans.get(0).getAttributes().get(SERVER_PORT))
                  .isEqualTo(Long.valueOf(port));
            });
  }

  @Test
  void trailingInvalidClusterEndpointOmitsStableTarget() {
    assumeTrue(emitStableDatabaseSemconv());

    TestRedisCluster redisCluster = new TestRedisCluster();
    cleanup.deferCleanup(redisCluster);
    Redis clusterClient =
        Redis.createClient(
            vertx,
            new RedisOptions()
                .setType(RedisClientType.CLUSTER)
                .addConnectionString(
                    "redis://" + redisCluster.getHost() + ":" + redisCluster.getPort())
                .addConnectionString("redis://"));
    cleanup.deferCleanup(clusterClient::close);

    RedisConnection clusterConnection =
        clusterClient.connect().toCompletionStage().toCompletableFuture().join();
    cleanup.deferCleanup(clusterConnection::close);
    clusterConnection
        .send(Request.cmd(Command.SET).arg("invalid-cluster-endpoint").arg("value"))
        .toCompletionStage()
        .toCompletableFuture()
        .join();

    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              List<SpanData> spans =
                  testing.spans().stream()
                      .filter(span -> span.getName().equals("SET"))
                      .collect(toList());
              assertThat(spans).hasSize(1);
              assertThat(spans.get(0).getAttributes().get(SERVER_ADDRESS)).isNull();
              assertThat(spans.get(0).getAttributes().get(SERVER_PORT)).isNull();
              assertThat(spans.get(0).getAttributes().get(NETWORK_PEER_ADDRESS))
                  .isEqualTo(redisCluster.getHost());
              assertThat(spans.get(0).getAttributes().get(NETWORK_PEER_PORT))
                  .isEqualTo(Long.valueOf(redisCluster.getPort()));
            });
    redisCluster.assertNoFailure();
  }
}
