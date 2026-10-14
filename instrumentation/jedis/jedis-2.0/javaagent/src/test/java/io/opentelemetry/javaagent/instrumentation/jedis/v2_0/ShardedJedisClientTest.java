/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v2_0;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.DbAttributes.DB_NAMESPACE;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_NAME;
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_TEXT;
import static io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues.REDIS;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.GenericContainer;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisShardInfo;
import redis.clients.jedis.ShardedJedis;
import redis.clients.jedis.ShardedJedisPipeline;

@SuppressWarnings("deprecation") // using deprecated semconv
class ShardedJedisClientTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension
  private static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  private static final GenericContainer<?> firstServer =
      new GenericContainer<>("redis:6.2.3-alpine").withExposedPorts(6379);

  private static final GenericContainer<?> secondServer =
      new GenericContainer<>("redis:6.2.3-alpine").withExposedPorts(6379);

  private static ShardedJedis sharded;
  private static Jedis shard;

  private static String configuredTarget;

  @BeforeAll
  static void setup() {
    firstServer.start();
    cleanup.deferAfterAll(firstServer::stop);
    secondServer.start();
    cleanup.deferAfterAll(secondServer::stop);

    JedisShardInfo firstShard =
        new JedisShardInfo(firstServer.getHost(), firstServer.getMappedPort(6379));
    JedisShardInfo secondShard =
        new JedisShardInfo(secondServer.getHost(), secondServer.getMappedPort(6379));
    configuredTarget =
        firstShard.getHost()
            + ":"
            + firstShard.getPort()
            + ","
            + secondShard.getHost()
            + ":"
            + secondShard.getPort();

    List<JedisShardInfo> shards = asList(firstShard, secondShard);
    sharded = new ShardedJedis(shards);
    cleanup.deferAfterAll(sharded::disconnect);

    shard = sharded.getShard("foo");
  }

  @Test
  void commandIsReportedAgainstEveryConfiguredShard() {
    sharded.set("foo", "bar");

    assertThat(sharded.get("foo")).isEqualTo("bar");
    assertThat(configuredTarget).contains(",");
    InetSocketAddress peerAddress =
        (InetSocketAddress) shard.getClient().getSocket().getRemoteSocketAddress();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("SET " + configuredTarget)
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM_NAME, REDIS),
                            equalTo(DB_QUERY_TEXT, "SET foo ?"),
                            equalTo(DB_OPERATION_NAME, "SET"),
                            equalTo(DB_NAMESPACE, "0"),
                            equalTo(SERVER_ADDRESS, configuredTarget),
                            equalTo(
                                NETWORK_PEER_ADDRESS, peerAddress.getAddress().getHostAddress()),
                            equalTo(NETWORK_PEER_PORT, (long) peerAddress.getPort()))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("GET " + configuredTarget)
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM_NAME, REDIS),
                            equalTo(DB_QUERY_TEXT, "GET foo"),
                            equalTo(DB_OPERATION_NAME, "GET"),
                            equalTo(DB_NAMESPACE, "0"),
                            equalTo(SERVER_ADDRESS, configuredTarget),
                            equalTo(
                                NETWORK_PEER_ADDRESS, peerAddress.getAddress().getHostAddress()),
                            equalTo(NETWORK_PEER_PORT, (long) peerAddress.getPort()))));
  }

  @Test
  void commandFromAllShardsUsesConfiguredTarget() {
    Jedis shard = sharded.getAllShards().iterator().next();

    shard.set("all-shards", "bar");
    InetSocketAddress peerAddress =
        (InetSocketAddress) shard.getClient().getSocket().getRemoteSocketAddress();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("SET " + configuredTarget)
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM_NAME, REDIS),
                            equalTo(DB_QUERY_TEXT, "SET all-shards ?"),
                            equalTo(DB_OPERATION_NAME, "SET"),
                            equalTo(DB_NAMESPACE, "0"),
                            equalTo(SERVER_ADDRESS, configuredTarget),
                            equalTo(
                                NETWORK_PEER_ADDRESS, peerAddress.getAddress().getHostAddress()),
                            equalTo(NETWORK_PEER_PORT, (long) peerAddress.getPort()))));

    testing.waitAndAssertMetrics(
        "io.opentelemetry.jedis-2.0",
        metric ->
            metric
                .hasName("db.client.operation.duration")
                .hasHistogramSatisfying(
                    histogram ->
                        histogram.hasPointsSatisfying(
                            point -> point.hasAttribute(SERVER_ADDRESS, configuredTarget))));
  }

  @Test
  void shardedPipelineFanOutWithinOneScopeKeepsPerCommandPeers() {
    List<Jedis> shards = new ArrayList<>(sharded.getAllShards());
    Jedis firstShard = shards.get(0);
    Jedis secondShard = shards.get(1);
    String firstKey = keyForShard(firstShard, "same-scope-first");
    String secondKey = keyForShard(secondShard, "same-scope-second");

    testing.runWithSpan(
        "parent",
        () ->
            sharded.pipelined(
                new ShardedJedisPipeline() {
                  @Override
                  public void execute() {
                    set(firstKey, "first");
                    set(secondKey, "second");
                  }
                }));
    InetSocketAddress firstPeerAddress =
        (InetSocketAddress) firstShard.getClient().getSocket().getRemoteSocketAddress();
    InetSocketAddress secondPeerAddress =
        (InetSocketAddress) secondShard.getClient().getSocket().getRemoteSocketAddress();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent().hasTotalAttributeCount(0),
                span ->
                    span.hasName("SET " + configuredTarget)
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM_NAME, REDIS),
                            equalTo(DB_QUERY_TEXT, "SET " + firstKey + " ?"),
                            equalTo(DB_OPERATION_NAME, "SET"),
                            equalTo(DB_NAMESPACE, "0"),
                            equalTo(SERVER_ADDRESS, configuredTarget),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                firstPeerAddress.getAddress().getHostAddress()),
                            equalTo(NETWORK_PEER_PORT, (long) firstPeerAddress.getPort())),
                span ->
                    span.hasName("SET " + configuredTarget)
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM_NAME, REDIS),
                            equalTo(DB_QUERY_TEXT, "SET " + secondKey + " ?"),
                            equalTo(DB_OPERATION_NAME, "SET"),
                            equalTo(DB_NAMESPACE, "0"),
                            equalTo(SERVER_ADDRESS, configuredTarget),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                secondPeerAddress.getAddress().getHostAddress()),
                            equalTo(NETWORK_PEER_PORT, (long) secondPeerAddress.getPort()))));
  }

  @Test
  void shardedPipelineFanOutAcrossScopesKeepsPerCommandPeers() {
    List<Jedis> shards = new ArrayList<>(sharded.getAllShards());
    Jedis firstShard = shards.get(0);
    Jedis secondShard = shards.get(1);
    String firstKey = keyForShard(firstShard, "cross-scope-first");
    String secondKey = keyForShard(secondShard, "cross-scope-second");

    sharded.pipelined(
        new ShardedJedisPipeline() {
          @Override
          public void execute() {
            testing.runWithSpan("first parent", () -> set(firstKey, "first"));
            testing.runWithSpan("second parent", () -> set(secondKey, "second"));
          }
        });
    InetSocketAddress firstPeerAddress =
        (InetSocketAddress) firstShard.getClient().getSocket().getRemoteSocketAddress();
    InetSocketAddress secondPeerAddress =
        (InetSocketAddress) secondShard.getClient().getSocket().getRemoteSocketAddress();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("first parent").hasNoParent().hasTotalAttributeCount(0),
                span ->
                    span.hasName("SET " + configuredTarget)
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM_NAME, REDIS),
                            equalTo(DB_QUERY_TEXT, "SET " + firstKey + " ?"),
                            equalTo(DB_OPERATION_NAME, "SET"),
                            equalTo(DB_NAMESPACE, "0"),
                            equalTo(SERVER_ADDRESS, configuredTarget),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                firstPeerAddress.getAddress().getHostAddress()),
                            equalTo(NETWORK_PEER_PORT, (long) firstPeerAddress.getPort()))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("second parent").hasNoParent().hasTotalAttributeCount(0),
                span ->
                    span.hasName("SET " + configuredTarget)
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM_NAME, REDIS),
                            equalTo(DB_QUERY_TEXT, "SET " + secondKey + " ?"),
                            equalTo(DB_OPERATION_NAME, "SET"),
                            equalTo(DB_NAMESPACE, "0"),
                            equalTo(SERVER_ADDRESS, configuredTarget),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                secondPeerAddress.getAddress().getHostAddress()),
                            equalTo(NETWORK_PEER_PORT, (long) secondPeerAddress.getPort()))));
  }

  private static String keyForShard(Jedis selectedShard, String prefix) {
    for (int i = 0; i < 1000; i++) {
      String key = prefix + "-" + i;
      if (sharded.getShard(key) == selectedShard) {
        return key;
      }
    }
    throw new AssertionError("Could not find a key for selected shard");
  }
}
