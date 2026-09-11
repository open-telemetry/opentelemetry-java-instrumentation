/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v1_4;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Connection;

class JedisSingletonsTest {

  @Test
  void captureConnectionTargetFallsBackToHostAndPortWhenNoScopeIsActive() {
    Connection connection = new Connection("direct", 6380);

    JedisSingletons.captureConnectionTarget(connection);

    RedisServerTarget target = JedisSingletons.connectionTarget(connection);
    assertThat(target.getAddress()).isEqualTo("direct");
    assertThat(target.getPort()).isEqualTo(6380);
  }

  @Test
  void captureConnectionTargetUsesActiveScopeInsteadOfHostAndPort() {
    Connection connection = new Connection("direct", 6379);
    RedisServerTarget configuredTarget = RedisServerTarget.ofHostAndPort("configured", 6380);

    try (Scope ignored = JedisSingletons.openConfiguredTargetScope(configuredTarget)) {
      JedisSingletons.captureConnectionTarget(connection);
    }

    assertThat(JedisSingletons.connectionTarget(connection)).isSameAs(configuredTarget);
  }

  @Test
  void connectionTargetPrefersActiveScopeOverAttachedTargetThenFallsBackOnClose() {
    Connection connection = new Connection("direct", 6379);
    RedisServerTarget attachedTarget = RedisServerTarget.ofHostAndPort("attached", 6380);
    RedisServerTarget scopedTarget = RedisServerTarget.ofHostAndPort("scoped", 6381);

    try (Scope ignored = JedisSingletons.openConfiguredTargetScope(attachedTarget)) {
      JedisSingletons.captureConnectionTarget(connection);
    }
    assertThat(JedisSingletons.connectionTarget(connection)).isSameAs(attachedTarget);

    try (Scope ignored = JedisSingletons.openConfiguredTargetScope(scopedTarget)) {
      assertThat(JedisSingletons.connectionTarget(connection)).isSameAs(scopedTarget);
    }

    assertThat(JedisSingletons.connectionTarget(connection)).isSameAs(attachedTarget);
  }
}
