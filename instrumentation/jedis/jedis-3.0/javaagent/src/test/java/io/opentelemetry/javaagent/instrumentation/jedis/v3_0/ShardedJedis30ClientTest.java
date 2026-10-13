/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v3_0;

import static io.opentelemetry.instrumentation.testing.junit.db.DbClientMetricsTestUtil.assertDurationMetric;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.DbAttributes.DB_NAMESPACE;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_NAME;
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_TEXT;
import static io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues.REDIS;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import org.assertj.core.api.AbstractLongAssert;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.GenericContainer;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisShardInfo;
import redis.clients.jedis.ShardedJedis;

@SuppressWarnings("deprecation") // using deprecated semconv
class ShardedJedis30ClientTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension
  private static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  private static final GenericContainer<?> firstServer =
      new GenericContainer<>("redis:6.2.3-alpine").withExposedPorts(6379);

  private static final GenericContainer<?> secondServer =
      new GenericContainer<>("redis:6.2.3-alpine").withExposedPorts(6379);

  private static ShardedJedis sharded;

  private static String configuredTarget;

  private static String shardHost;
  private static String shardIp;

  @BeforeAll
  static void setup() throws UnknownHostException {
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
    cleanup.deferAfterAll(sharded);

    Jedis shard = sharded.getShard("foo");
    shardHost = shard.getClient().getHost();
    shardIp = InetAddress.getByName(shardHost).getHostAddress();
  }

  @Test
  void commandIsReportedAgainstEveryConfiguredShard() {
    sharded.set("foo", "bar");

    assertThat(sharded.get("foo")).isEqualTo("bar");
    assertThat(configuredTarget).contains(",");

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
                            equalTo(NETWORK_PEER_ADDRESS, shardIp),
                            satisfies(NETWORK_PEER_PORT, AbstractLongAssert::isNotNegative))),
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
                            equalTo(NETWORK_PEER_ADDRESS, shardIp),
                            satisfies(NETWORK_PEER_PORT, AbstractLongAssert::isNotNegative))));
  }

  @Test
  void commandFromAllShardsUsesConfiguredTarget() {
    Jedis shard = sharded.getAllShards().iterator().next();

    shard.set("all-shards", "bar");

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
                            equalTo(NETWORK_PEER_ADDRESS, shardIp),
                            satisfies(NETWORK_PEER_PORT, AbstractLongAssert::isNotNegative))));

    assertDurationMetric(
        testing,
        "io.opentelemetry.jedis-3.0",
        DB_SYSTEM_NAME,
        DB_NAMESPACE,
        DB_OPERATION_NAME,
        SERVER_ADDRESS,
        NETWORK_PEER_ADDRESS,
        NETWORK_PEER_PORT);
    testing.waitAndAssertMetrics(
        "io.opentelemetry.jedis-3.0",
        metric ->
            metric
                .hasName("db.client.operation.duration")
                .hasHistogramSatisfying(
                    histogram ->
                        histogram.hasPointsSatisfying(
                            point ->
                                point
                                    .hasAttribute(SERVER_ADDRESS, configuredTarget)
                                    .hasAttributesSatisfying(
                                        attributes ->
                                            assertThat(attributes.asMap())
                                                .doesNotContainKey(SERVER_PORT)))));
  }
}
