/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v3_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldDatabaseSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStable;
import static io.opentelemetry.instrumentation.testing.junit.service.SemconvServiceStabilityUtil.maybeStablePeerService;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.DbAttributes.DB_NAMESPACE;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_TYPE;
import static io.opentelemetry.semconv.NetworkAttributes.NetworkTypeValues.IPV4;
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
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
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
  private static int shardPort;
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
    shardPort = shard.getClient().getPort();
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
                    span.hasName(emitStableDatabaseSemconv() ? "SET " + configuredTarget : "SET")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            attributes("SET", "SET foo ?", shardHost, shardPort))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(emitStableDatabaseSemconv() ? "GET " + configuredTarget : "GET")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            attributes("GET", "GET foo", shardHost, shardPort))));
  }

  @Test
  void commandFromAllShardsUsesConfiguredTarget() {
    Jedis shard = sharded.getAllShards().iterator().next();
    String selectedHost = shard.getClient().getHost();
    int selectedPort = shard.getClient().getPort();

    shard.set("all-shards", "bar");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(emitStableDatabaseSemconv() ? "SET " + configuredTarget : "SET")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            attributes("SET", "SET all-shards ?", selectedHost, selectedPort))));
  }

  private static List<AttributeAssertion> attributes(
      String operation, String queryText, String selectedHost, int selectedPort) {
    return asList(
        equalTo(maybeStable(DB_SYSTEM), REDIS),
        equalTo(maybeStable(DB_STATEMENT), queryText),
        equalTo(maybeStable(DB_OPERATION), operation),
        equalTo(DB_NAMESPACE, emitStableDatabaseSemconv() ? "0" : null),
        equalTo(maybeStablePeerService(), emitStableDatabaseSemconv() ? null : "test-peer-service"),
        equalTo(SERVER_ADDRESS, emitStableDatabaseSemconv() ? configuredTarget : selectedHost),
        equalTo(SERVER_PORT, emitStableDatabaseSemconv() ? null : (long) selectedPort),
        equalTo(NETWORK_TYPE, emitOldDatabaseSemconv() ? IPV4 : null),
        equalTo(NETWORK_PEER_ADDRESS, shardIp),
        satisfies(NETWORK_PEER_PORT, AbstractLongAssert::isNotNegative));
  }
}
