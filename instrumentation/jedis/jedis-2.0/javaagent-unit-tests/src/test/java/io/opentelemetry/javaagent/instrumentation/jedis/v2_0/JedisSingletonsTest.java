/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v2_0;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Connection;
import redis.clients.jedis.JedisPool;

class JedisSingletonsTest {

  @Test
  void invalidUpdatesClearCapturedConnectionTarget() {
    Connection connection = new Connection("localhost", 6380);

    JedisSingletons.captureConnectionTarget(connection);
    assertThat(JedisSingletons.connectionTarget(connection)).isNotNull();

    connection.setHost(null);
    JedisSingletons.captureConnectionTarget(connection);
    assertThat(JedisSingletons.connectionTarget(connection)).isNull();

    connection.setHost("localhost");
    JedisSingletons.captureConnectionTarget(connection);
    assertThat(JedisSingletons.connectionTarget(connection)).isNotNull();

    connection.setPort(65536);
    JedisSingletons.captureConnectionTarget(connection);
    assertThat(JedisSingletons.connectionTarget(connection)).isNull();
  }

  @Test
  void unavailableConfiguredTargetSuppressesConnectionFallback() {
    Connection connection = new Connection("localhost", 6380);
    Context context = JedisSingletons.configuredTargetContext(null);

    try (Scope ignored = context.makeCurrent()) {
      JedisSingletons.captureConnectionTarget(connection);
      assertThat(JedisSingletons.connectionTarget(connection)).isNull();
    }
  }

  @Test
  void poolPreservesUnavailableConfiguredTarget() {
    JedisPool pool = new JedisPool("localhost", 6379);
    try {
      assertThat(JedisSingletons.configuredPoolTargetContext(pool)).isNull();

      JedisSingletons.setPoolTarget(pool, null);
      Context context = JedisSingletons.configuredPoolTargetContext(pool);
      assertThat(context).isNotNull();

      Connection connection = new Connection("localhost", 6380);
      try (Scope ignored = context.makeCurrent()) {
        JedisSingletons.captureConnectionTarget(connection);
        assertThat(JedisSingletons.connectionTarget(connection)).isNull();
      }
    } finally {
      pool.destroy();
    }
  }
}
