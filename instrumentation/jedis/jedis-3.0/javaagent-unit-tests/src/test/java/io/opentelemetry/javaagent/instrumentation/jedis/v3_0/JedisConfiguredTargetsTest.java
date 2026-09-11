/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v3_0;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singleton;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import java.util.Set;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Connection;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Protocol;

class JedisConfiguredTargetsTest {

  @Test
  void replacesConnectionTargetAcrossReuse() {
    Connection connection = new Connection("first", 6379);

    JedisConfiguredTargets.setConnectionTarget(
        connection, RedisServerTarget.ofEndpoint("first:6379"));
    assertThat(JedisConfiguredTargets.connectionTarget(connection))
        .extracting(RedisServerTarget::getAddress)
        .isEqualTo("first");

    JedisConfiguredTargets.setConnectionTarget(connection, null);
    assertThat(JedisConfiguredTargets.connectionTarget(connection)).isNull();

    JedisConfiguredTargets.setConnectionTarget(
        connection, RedisServerTarget.ofEndpoint("second:6380"));
    assertThat(JedisConfiguredTargets.connectionTarget(connection))
        .extracting(RedisServerTarget::getAddress)
        .isEqualTo("second");
    assertThat(JedisConfiguredTargets.connectionTarget(connection))
        .extracting(RedisServerTarget::getPort)
        .isEqualTo(6380);
  }

  @Test
  void scopedTargetDoesNotReplaceConnectionTarget() {
    Connection connection = new Connection("direct", 6379);
    JedisConfiguredTargets.setConnectionTarget(
        connection, RedisServerTarget.ofEndpoint("direct:6379"));

    try (Scope scope =
        JedisConfiguredTargets.openConfiguredTargetScope(
            RedisServerTarget.ofEndpoints(asList("configured-one:6379", "configured-two:6380")))) {
      assertThat(JedisConfiguredTargets.connectionTarget(connection))
          .extracting(RedisServerTarget::getAddress)
          .isEqualTo("configured-one:6379,configured-two:6380");
    }

    assertThat(JedisConfiguredTargets.connectionTarget(connection))
        .extracting(RedisServerTarget::getAddress)
        .isEqualTo("direct");
  }

  @Test
  void requestKeepsCapturedConnectionTarget() {
    Connection connection = new Connection("direct", 6379);
    JedisConfiguredTargets.setConnectionTarget(
        connection, RedisServerTarget.ofEndpoint("configured:6379"));

    JedisRequest request = JedisRequest.create(connection, Protocol.Command.GET, emptyList());
    JedisConfiguredTargets.setConnectionTarget(
        connection, RedisServerTarget.ofEndpoint("replacement:6379"));

    assertThat(request.getServerTarget())
        .extracting(RedisServerTarget::getAddress)
        .isEqualTo("configured");
  }

  @Test
  void parsedSentinelUsesOriginalEndpoint() {
    HostAndPort parsedSentinel = new HostAndPort("192.0.2.1", 26379);
    JedisConfiguredTargets.captureOriginalEndpoint(parsedSentinel, "sentinel.example:26379");
    Set<HostAndPort> parsedSentinels = singleton(parsedSentinel);

    assertThat(JedisConfiguredTargets.sentinelTarget("mymaster", parsedSentinels))
        .extracting(RedisServerTarget::getAddress)
        .isEqualTo("sentinel.example:26379/mymaster");
  }
}
