/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v4_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStable;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.DbAttributes.DB_NAMESPACE;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_BATCH_SIZE;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues.REDIS;
import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import com.lambdaworks.redis.RedisFuture;
import com.lambdaworks.redis.RedisURI;
import com.lambdaworks.redis.cluster.RedisClusterClient;
import com.lambdaworks.redis.cluster.api.StatefulRedisClusterConnection;
import com.lambdaworks.redis.cluster.api.async.RedisAdvancedClusterAsyncCommands;
import com.lambdaworks.redis.cluster.models.partitions.Partitions;
import com.lambdaworks.redis.cluster.models.partitions.RedisClusterNode;
import com.lambdaworks.redis.cluster.models.partitions.RedisClusterNode.NodeFlag;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

@SuppressWarnings("deprecation") // using deprecated semconv
class LettuceClusterClientTest {
  private static final Logger logger = LoggerFactory.getLogger(LettuceClusterClientTest.class);

  private static final String NODE_ID = "0000000000000000000000000000000000000000";

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  private static final DockerImageName CONTAINER_IMAGE =
      DockerImageName.parse("redis:6.2.3-alpine");

  private static final GenericContainer<?> redisServer =
      new GenericContainer<>(CONTAINER_IMAGE)
          .withExposedPorts(6379)
          .withLogConsumer(new Slf4jLogConsumer(logger))
          .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*", 1));

  private static StatefulRedisClusterConnection<String, String> connection;
  private static String host;
  private static int port;
  private static String configuredTarget;

  @BeforeAll
  static void setUp() throws Exception {
    redisServer.start();
    cleanup.deferAfterAll(redisServer::stop);

    host = redisServer.getHost();
    port = redisServer.getMappedPort(6379);

    RedisURI nodeUri = RedisURI.create("redis://" + host + ":" + port);
    RedisURI alternateSeed = RedisURI.create("redis://seed.invalid:6379");
    configuredTarget = "seed.invalid:6379," + host + ":" + port;
    RedisClusterClient client = new TestRedisClusterClient(asList(alternateSeed, nodeUri), nodeUri);
    cleanup.deferAfterAll(() -> client.shutdown(0, 15, SECONDS));

    connection = client.connect();
    cleanup.deferAfterAll(connection);
  }

  @Test
  void testCommandUsesConfiguredSeedList() throws Exception {
    RedisAdvancedClusterAsyncCommands<String, String> asyncCommands = connection.async();
    assertThat(asyncCommands.set("CLUSTER_COMMAND_KEY", "value").get(10, SECONDS)).isEqualTo("OK");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(emitStableDatabaseSemconv() ? "SET " + configuredTarget : "SET")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), REDIS),
                            equalTo(DB_NAMESPACE, null),
                            equalTo(maybeStable(DB_OPERATION), "SET"),
                            equalTo(
                                SERVER_ADDRESS,
                                emitStableDatabaseSemconv() ? configuredTarget : host),
                            equalTo(SERVER_PORT, emitStableDatabaseSemconv() ? null : port))));
  }

  @Test
  void testBatchUsesConfiguredSeedList() throws Exception {
    RedisAdvancedClusterAsyncCommands<String, String> asyncCommands = connection.async();
    asyncCommands.setAutoFlushCommands(false);
    cleanup.deferCleanup(() -> asyncCommands.setAutoFlushCommands(true));
    RedisFuture<String> first = asyncCommands.set("CLUSTER_BATCH_KEY_1", "value");
    RedisFuture<String> second = asyncCommands.set("CLUSTER_BATCH_KEY_2", "value");
    asyncCommands.flushCommands();
    assertThat(first.get(10, SECONDS)).isEqualTo("OK");
    assertThat(second.get(10, SECONDS)).isEqualTo("OK");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(
                            emitStableDatabaseSemconv()
                                ? "PIPELINE SET " + configuredTarget
                                : "PIPELINE SET")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), REDIS),
                            equalTo(DB_NAMESPACE, null),
                            equalTo(maybeStable(DB_OPERATION), "PIPELINE SET"),
                            equalTo(
                                SERVER_ADDRESS,
                                emitStableDatabaseSemconv() ? configuredTarget : host),
                            equalTo(SERVER_PORT, emitStableDatabaseSemconv() ? null : port),
                            equalTo(
                                DB_OPERATION_BATCH_SIZE,
                                emitStableDatabaseSemconv() ? 2L : null))));
  }

  private static class TestRedisClusterClient extends RedisClusterClient {
    private final RedisURI nodeUri;

    private TestRedisClusterClient(List<RedisURI> seedUris, RedisURI nodeUri) {
      super(seedUris);
      this.nodeUri = nodeUri;
    }

    @Override
    protected Partitions loadPartitions() {
      List<Integer> slots = new ArrayList<>(16384);
      for (int slot = 0; slot < 16384; slot++) {
        slots.add(slot);
      }
      RedisClusterNode node = new RedisClusterNode();
      node.setUri(nodeUri);
      node.setNodeId(NODE_ID);
      node.setConnected(true);
      node.setSlots(slots);
      node.setFlags(EnumSet.of(NodeFlag.MASTER));

      Partitions partitions = new Partitions();
      partitions.addPartition(node);
      partitions.updateCache();
      return partitions;
    }
  }
}
