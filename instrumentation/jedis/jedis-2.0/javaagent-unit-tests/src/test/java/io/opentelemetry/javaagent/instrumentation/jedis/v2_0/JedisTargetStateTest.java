/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v2_0;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Connection;

class JedisTargetStateTest {

  @Test
  void connectionTargetIsReplacedAsOneSnapshot() {
    Connection connection = new Connection("direct", 6379);
    RedisServerTarget firstTarget = RedisServerTarget.ofHostAndPort("first", 6380);
    RedisServerTarget secondTarget = RedisServerTarget.ofHostAndPort("second", 6381);

    JedisSingletons.setConnectionTarget(connection, firstTarget);
    assertThat(JedisSingletons.connectionTarget(connection)).isSameAs(firstTarget);

    JedisSingletons.setConnectionTarget(connection, null);
    assertThat(JedisSingletons.connectionTarget(connection)).isNull();

    JedisSingletons.setConnectionTarget(connection, secondTarget);
    assertThat(JedisSingletons.connectionTarget(connection)).isSameAs(secondTarget);
  }
}
