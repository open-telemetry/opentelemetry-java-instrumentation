/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v2_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStable;
import static io.opentelemetry.instrumentation.testing.junit.service.SemconvServiceStabilityUtil.maybeStablePeerService;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.DbAttributes.DB_NAMESPACE;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_STATEMENT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;
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

  private static String shardHost;
  private static int shardPort;

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
    shardHost = shard.getClient().getHost();
    shardPort = shard.getClient().getPort();
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
                    span.hasName(emitStableDatabaseSemconv() ? "SET " + configuredTarget : "SET")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), REDIS),
                            equalTo(maybeStable(DB_STATEMENT), "SET foo ?"),
                            equalTo(maybeStable(DB_OPERATION), "SET"),
                            equalTo(DB_NAMESPACE, emitStableDatabaseSemconv() ? "0" : null),
                            equalTo(
                                maybeStablePeerService(),
                                emitStableDatabaseSemconv() ? null : "test-peer-service"),
                            equalTo(
                                SERVER_ADDRESS,
                                emitStableDatabaseSemconv() ? configuredTarget : shardHost),
                            equalTo(
                                SERVER_PORT, emitStableDatabaseSemconv() ? null : (long) shardPort),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                emitStableDatabaseSemconv()
                                    ? peerAddress.getAddress().getHostAddress()
                                    : null),
                            equalTo(
                                NETWORK_PEER_PORT,
                                emitStableDatabaseSemconv()
                                    ? (long) peerAddress.getPort()
                                    : null))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(emitStableDatabaseSemconv() ? "GET " + configuredTarget : "GET")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), REDIS),
                            equalTo(maybeStable(DB_STATEMENT), "GET foo"),
                            equalTo(maybeStable(DB_OPERATION), "GET"),
                            equalTo(DB_NAMESPACE, emitStableDatabaseSemconv() ? "0" : null),
                            equalTo(
                                maybeStablePeerService(),
                                emitStableDatabaseSemconv() ? null : "test-peer-service"),
                            equalTo(
                                SERVER_ADDRESS,
                                emitStableDatabaseSemconv() ? configuredTarget : shardHost),
                            equalTo(
                                SERVER_PORT, emitStableDatabaseSemconv() ? null : (long) shardPort),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                emitStableDatabaseSemconv()
                                    ? peerAddress.getAddress().getHostAddress()
                                    : null),
                            equalTo(
                                NETWORK_PEER_PORT,
                                emitStableDatabaseSemconv()
                                    ? (long) peerAddress.getPort()
                                    : null))));
  }

  @Test
  void commandFromAllShardsUsesConfiguredTarget() {
    Jedis shard = sharded.getAllShards().iterator().next();
    String selectedHost = shard.getClient().getHost();
    int selectedPort = shard.getClient().getPort();

    shard.set("all-shards", "bar");
    InetSocketAddress peerAddress =
        (InetSocketAddress) shard.getClient().getSocket().getRemoteSocketAddress();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(emitStableDatabaseSemconv() ? "SET " + configuredTarget : "SET")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), REDIS),
                            equalTo(maybeStable(DB_STATEMENT), "SET all-shards ?"),
                            equalTo(maybeStable(DB_OPERATION), "SET"),
                            equalTo(DB_NAMESPACE, emitStableDatabaseSemconv() ? "0" : null),
                            equalTo(
                                maybeStablePeerService(),
                                emitStableDatabaseSemconv() ? null : "test-peer-service"),
                            equalTo(
                                SERVER_ADDRESS,
                                emitStableDatabaseSemconv() ? configuredTarget : selectedHost),
                            equalTo(
                                SERVER_PORT,
                                emitStableDatabaseSemconv() ? null : (long) selectedPort),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                emitStableDatabaseSemconv()
                                    ? peerAddress.getAddress().getHostAddress()
                                    : null),
                            equalTo(
                                NETWORK_PEER_PORT,
                                emitStableDatabaseSemconv()
                                    ? (long) peerAddress.getPort()
                                    : null))));
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
                    span.hasName(emitStableDatabaseSemconv() ? "SET " + configuredTarget : "SET")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), REDIS),
                            equalTo(maybeStable(DB_STATEMENT), "SET " + firstKey + " ?"),
                            equalTo(maybeStable(DB_OPERATION), "SET"),
                            equalTo(DB_NAMESPACE, emitStableDatabaseSemconv() ? "0" : null),
                            equalTo(
                                maybeStablePeerService(),
                                emitStableDatabaseSemconv() ? null : "test-peer-service"),
                            equalTo(
                                SERVER_ADDRESS,
                                emitStableDatabaseSemconv()
                                    ? configuredTarget
                                    : firstShard.getClient().getHost()),
                            equalTo(
                                SERVER_PORT,
                                emitStableDatabaseSemconv()
                                    ? null
                                    : (long) firstShard.getClient().getPort()),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                emitStableDatabaseSemconv()
                                    ? firstPeerAddress.getAddress().getHostAddress()
                                    : null),
                            equalTo(
                                NETWORK_PEER_PORT,
                                emitStableDatabaseSemconv()
                                    ? (long) firstPeerAddress.getPort()
                                    : null)),
                span ->
                    span.hasName(emitStableDatabaseSemconv() ? "SET " + configuredTarget : "SET")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), REDIS),
                            equalTo(maybeStable(DB_STATEMENT), "SET " + secondKey + " ?"),
                            equalTo(maybeStable(DB_OPERATION), "SET"),
                            equalTo(DB_NAMESPACE, emitStableDatabaseSemconv() ? "0" : null),
                            equalTo(
                                maybeStablePeerService(),
                                emitStableDatabaseSemconv() ? null : "test-peer-service"),
                            equalTo(
                                SERVER_ADDRESS,
                                emitStableDatabaseSemconv()
                                    ? configuredTarget
                                    : secondShard.getClient().getHost()),
                            equalTo(
                                SERVER_PORT,
                                emitStableDatabaseSemconv()
                                    ? null
                                    : (long) secondShard.getClient().getPort()),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                emitStableDatabaseSemconv()
                                    ? secondPeerAddress.getAddress().getHostAddress()
                                    : null),
                            equalTo(
                                NETWORK_PEER_PORT,
                                emitStableDatabaseSemconv()
                                    ? (long) secondPeerAddress.getPort()
                                    : null))));
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
                    span.hasName(emitStableDatabaseSemconv() ? "SET " + configuredTarget : "SET")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), REDIS),
                            equalTo(maybeStable(DB_STATEMENT), "SET " + firstKey + " ?"),
                            equalTo(maybeStable(DB_OPERATION), "SET"),
                            equalTo(DB_NAMESPACE, emitStableDatabaseSemconv() ? "0" : null),
                            equalTo(
                                maybeStablePeerService(),
                                emitStableDatabaseSemconv() ? null : "test-peer-service"),
                            equalTo(
                                SERVER_ADDRESS,
                                emitStableDatabaseSemconv()
                                    ? configuredTarget
                                    : firstShard.getClient().getHost()),
                            equalTo(
                                SERVER_PORT,
                                emitStableDatabaseSemconv()
                                    ? null
                                    : (long) firstShard.getClient().getPort()),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                emitStableDatabaseSemconv()
                                    ? firstPeerAddress.getAddress().getHostAddress()
                                    : null),
                            equalTo(
                                NETWORK_PEER_PORT,
                                emitStableDatabaseSemconv()
                                    ? (long) firstPeerAddress.getPort()
                                    : null))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("second parent").hasNoParent().hasTotalAttributeCount(0),
                span ->
                    span.hasName(emitStableDatabaseSemconv() ? "SET " + configuredTarget : "SET")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), REDIS),
                            equalTo(maybeStable(DB_STATEMENT), "SET " + secondKey + " ?"),
                            equalTo(maybeStable(DB_OPERATION), "SET"),
                            equalTo(DB_NAMESPACE, emitStableDatabaseSemconv() ? "0" : null),
                            equalTo(
                                maybeStablePeerService(),
                                emitStableDatabaseSemconv() ? null : "test-peer-service"),
                            equalTo(
                                SERVER_ADDRESS,
                                emitStableDatabaseSemconv()
                                    ? configuredTarget
                                    : secondShard.getClient().getHost()),
                            equalTo(
                                SERVER_PORT,
                                emitStableDatabaseSemconv()
                                    ? null
                                    : (long) secondShard.getClient().getPort()),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                emitStableDatabaseSemconv()
                                    ? secondPeerAddress.getAddress().getHostAddress()
                                    : null),
                            equalTo(
                                NETWORK_PEER_PORT,
                                emitStableDatabaseSemconv()
                                    ? (long) secondPeerAddress.getPort()
                                    : null))));
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
