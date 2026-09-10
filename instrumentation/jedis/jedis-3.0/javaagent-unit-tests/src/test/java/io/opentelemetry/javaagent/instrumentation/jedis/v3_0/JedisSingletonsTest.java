/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v3_0;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Connection;

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
}
