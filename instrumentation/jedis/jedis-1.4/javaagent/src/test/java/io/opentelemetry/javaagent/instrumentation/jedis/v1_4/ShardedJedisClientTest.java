/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v1_4;

import static io.opentelemetry.instrumentation.testing.junit.service.SemconvServiceStabilityUtil.maybeStablePeerService;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_NAME;
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_TEXT;
import static io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues.REDIS;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
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
  }

  @Test
  void commandIsReportedAgainstEveryConfiguredShard() {
    sharded.set("foo", "bar");

    assertThat(sharded.get("foo")).isEqualTo("bar");

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
                            equalTo(SERVER_ADDRESS, configuredTarget))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("GET " + configuredTarget)
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM_NAME, REDIS),
                            equalTo(DB_QUERY_TEXT, "GET foo"),
                            equalTo(DB_OPERATION_NAME, "GET"),
                            equalTo(SERVER_ADDRESS, configuredTarget))));

    testing.waitAndAssertMetrics(
        "io.opentelemetry.jedis-1.4",
        metric ->
            metric
                .hasName("db.client.operation.duration")
                .hasHistogramSatisfying(
                    histogram ->
                        histogram.hasPointsSatisfying(
                            point ->
                                point.hasAttributesSatisfyingExactly(
                                    equalTo(DB_SYSTEM_NAME, REDIS),
                                    equalTo(DB_OPERATION_NAME, "SET"),
                                    equalTo(SERVER_ADDRESS, configuredTarget)),
                            point ->
                                point.hasAttributesSatisfyingExactly(
                                    equalTo(DB_SYSTEM_NAME, REDIS),
                                    equalTo(DB_OPERATION_NAME, "GET"),
                                    equalTo(SERVER_ADDRESS, configuredTarget)))));
  }

  @Test
  void commandFromAllShardsUsesConfiguredTarget() throws ReflectiveOperationException {
    Object returnedShard = sharded.getAllShards().iterator().next();
    Jedis shard;
    if (returnedShard instanceof Jedis) {
      shard = (Jedis) returnedShard;
    } else {
      assertThat(returnedShard).isInstanceOf(JedisShardInfo.class);
      shard = (Jedis) returnedShard.getClass().getMethod("getResource").invoke(returnedShard);
    }

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
                            equalTo(SERVER_ADDRESS, configuredTarget))));
  }

  @Test
  void callbackConnectionIsNotAssignedOuterTarget() {
    CapturingJedisShardInfo nestedShard =
        new CapturingJedisShardInfo(firstServer.getHost(), firstServer.getMappedPort(6379));
    ReentrantJedisShardInfo outerShard =
        new ReentrantJedisShardInfo(
            firstServer.getHost(), firstServer.getMappedPort(6379), nestedShard);
    CapturingJedisShardInfo secondOuterShard =
        new CapturingJedisShardInfo(secondServer.getHost(), secondServer.getMappedPort(6379));
    new ShardedJedis(asList(outerShard, secondOuterShard));

    cleanup.deferCleanup(outerShard.createdResource().getClient()::disconnect);
    cleanup.deferCleanup(secondOuterShard.createdResource().getClient()::disconnect);
    cleanup.deferCleanup(nestedShard.createdResource().getClient()::disconnect);
    cleanup.deferCleanup(outerShard.callbackJedis()::disconnect);

    outerShard.callbackJedis().set("callback", "bar");
    outerShard.createdResource().set("outer", "bar");

    String outerTarget =
        outerShard.getHost()
            + ":"
            + outerShard.getPort()
            + ","
            + secondOuterShard.getHost()
            + ":"
            + secondOuterShard.getPort();
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("SET " + outerShard.getHost() + ":" + outerShard.getPort())
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM_NAME, REDIS),
                            equalTo(DB_QUERY_TEXT, "SET callback ?"),
                            equalTo(DB_OPERATION_NAME, "SET"),
                            equalTo(maybeStablePeerService(), "test-peer-service"),
                            equalTo(SERVER_ADDRESS, outerShard.getHost()),
                            equalTo(SERVER_PORT, outerShard.getPort()))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("SET " + outerTarget)
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM_NAME, REDIS),
                            equalTo(DB_QUERY_TEXT, "SET outer ?"),
                            equalTo(DB_OPERATION_NAME, "SET"),
                            equalTo(SERVER_ADDRESS, outerTarget))));
  }

  private static class CapturingJedisShardInfo extends JedisShardInfo {

    private Jedis createdResource;

    private CapturingJedisShardInfo(String host, int port) {
      super(host, port);
    }

    @Override
    public Jedis createResource() {
      createdResource = super.createResource();
      return createdResource;
    }

    final Jedis createdResource() {
      return createdResource;
    }
  }

  private static class ReentrantJedisShardInfo extends CapturingJedisShardInfo {

    private final JedisShardInfo nestedShard;
    private Jedis callbackJedis;

    private ReentrantJedisShardInfo(String host, int port, JedisShardInfo nestedShard) {
      super(host, port);
      this.nestedShard = nestedShard;
    }

    @Override
    public Jedis createResource() {
      new ShardedJedis(singletonList(nestedShard));
      callbackJedis = new Jedis(getHost(), getPort());
      return super.createResource();
    }

    private Jedis callbackJedis() {
      return callbackJedis;
    }
  }
}
