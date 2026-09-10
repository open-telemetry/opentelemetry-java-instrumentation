/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v3_0;

import static java.util.Arrays.asList;
import static java.util.Collections.singleton;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import java.util.Set;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Connection;
import redis.clients.jedis.HostAndPort;

class JedisSingletonsTest {

  @Test
  void replacesConnectionTargetAcrossReuse() {
    Connection connection = new Connection("first", 6379);

    JedisSingletons.setConnectionTarget(connection, RedisServerTarget.ofEndpoint("first:6379"));
    assertThat(JedisSingletons.connectionTarget(connection))
        .extracting(RedisServerTarget::getAddress)
        .isEqualTo("first");

    JedisSingletons.setConnectionTarget(connection, null);
    assertThat(JedisSingletons.connectionTarget(connection)).isNull();

    JedisSingletons.setConnectionTarget(connection, RedisServerTarget.ofEndpoint("second:6380"));
    assertThat(JedisSingletons.connectionTarget(connection))
        .extracting(RedisServerTarget::getAddress)
        .isEqualTo("second");
    assertThat(JedisSingletons.connectionTarget(connection))
        .extracting(RedisServerTarget::getPort)
        .isEqualTo(6380);
  }

  @Test
  void scopedTargetDoesNotReplaceConnectionTarget() {
    Connection connection = new Connection("direct", 6379);
    JedisSingletons.setConnectionTarget(connection, RedisServerTarget.ofEndpoint("direct:6379"));

    try (Scope scope =
        JedisSingletons.openConfiguredTargetScope(
            RedisServerTarget.ofEndpoints(asList("configured-one:6379", "configured-two:6380")))) {
      assertThat(JedisSingletons.connectionTarget(connection))
          .extracting(RedisServerTarget::getAddress)
          .isEqualTo("configured-one:6379,configured-two:6380");
    }

    assertThat(JedisSingletons.connectionTarget(connection))
        .extracting(RedisServerTarget::getAddress)
        .isEqualTo("direct");
  }

  @Test
  void parsedSentinelsUseConfiguredEndpoints() {
    Set<HostAndPort> parsedSentinels = singleton(new HostAndPort("192.0.2.1", 26379));
    JedisSingletons.registerParsedSentinels(parsedSentinels, singleton("sentinel.example:26379"));

    assertThat(JedisSingletons.sentinelTarget("mymaster", parsedSentinels))
        .extracting(RedisServerTarget::getAddress)
        .isEqualTo("sentinel.example:26379/mymaster");
  }
}
