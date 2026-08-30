/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v2_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldDatabaseSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStable;
import static io.opentelemetry.instrumentation.testing.junit.service.SemconvServiceStabilityUtil.maybeStablePeerService;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.DbAttributes.DB_NAMESPACE;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_TYPE;
import static io.opentelemetry.semconv.NetworkAttributes.NetworkTypeValues.IPV4;
import static io.opentelemetry.semconv.NetworkAttributes.NetworkTypeValues.IPV6;
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
import java.net.Inet4Address;
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

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(emitStableDatabaseSemconv() ? "SET " + configuredTarget : "SET")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(attributes("SET", "SET foo ?", shard))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(emitStableDatabaseSemconv() ? "GET " + configuredTarget : "GET")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(attributes("GET", "GET foo", shard))));
  }

  @Test
  void commandFromAllShardsUsesConfiguredTarget() {
    Jedis shard = sharded.getAllShards().iterator().next();

    shard.set("all-shards", "bar");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(emitStableDatabaseSemconv() ? "SET " + configuredTarget : "SET")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            attributes("SET", "SET all-shards ?", shard))));
  }

  private static List<AttributeAssertion> attributes(
      String operation, String queryText, Jedis selectedShard) {
    String selectedHost = selectedShard.getClient().getHost();
    int selectedPort = selectedShard.getClient().getPort();
    List<AttributeAssertion> assertions =
        new ArrayList<>(
            asList(
                equalTo(maybeStable(DB_SYSTEM), REDIS),
                equalTo(maybeStable(DB_STATEMENT), queryText),
                equalTo(maybeStable(DB_OPERATION), operation),
                equalTo(DB_NAMESPACE, emitStableDatabaseSemconv() ? "0" : null)));
    if (emitStableDatabaseSemconv() && emitOldDatabaseSemconv()) {
      assertions.add(equalTo(DB_SYSTEM, REDIS));
      assertions.add(equalTo(DB_STATEMENT, queryText));
      assertions.add(equalTo(DB_OPERATION, operation));
    }
    if (emitStableDatabaseSemconv()) {
      InetSocketAddress peerAddress =
          (InetSocketAddress) selectedShard.getClient().getSocket().getRemoteSocketAddress();
      assertions.add(equalTo(SERVER_ADDRESS, configuredTarget));
      assertions.add(equalTo(NETWORK_PEER_ADDRESS, peerAddress.getAddress().getHostAddress()));
      assertions.add(equalTo(NETWORK_PEER_PORT, peerAddress.getPort()));
      if (emitOldDatabaseSemconv()) {
        assertions.add(
            equalTo(NETWORK_TYPE, peerAddress.getAddress() instanceof Inet4Address ? IPV4 : IPV6));
      }
    } else {
      assertions.add(equalTo(maybeStablePeerService(), "test-peer-service"));
      assertions.add(equalTo(SERVER_ADDRESS, selectedHost));
      assertions.add(equalTo(SERVER_PORT, selectedPort));
    }
    return assertions;
  }
}
