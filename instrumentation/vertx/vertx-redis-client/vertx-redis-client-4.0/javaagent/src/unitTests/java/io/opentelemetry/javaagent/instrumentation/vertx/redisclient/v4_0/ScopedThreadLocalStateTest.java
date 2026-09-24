/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.redisclient.v4_0;

import static io.opentelemetry.javaagent.instrumentation.vertx.redisclient.v4_0.VertxRedisClientSingletons.currentRedisUri;
import static io.opentelemetry.javaagent.instrumentation.vertx.redisclient.v4_0.VertxRedisClientSingletons.currentServerTarget;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import io.vertx.redis.client.impl.RedisConnectionManagerUtil;
import io.vertx.redis.client.impl.RedisURI;
import org.junit.jupiter.api.Test;

class ScopedThreadLocalStateTest {

  @Test
  void restoresNestedServerTargets() throws ClassNotFoundException {
    Class<?> managerClass = Class.forName("io.vertx.redis.client.impl.RedisConnectionManager");
    Object outerManager = mock(managerClass, withSettings().withoutAnnotations());
    Object innerManager = mock(managerClass, withSettings().withoutAnnotations());
    RedisServerTarget outerTarget = RedisServerTarget.ofHostAndPort("outer", 6379);
    RedisServerTarget innerTarget = RedisServerTarget.ofHostAndPort("inner", 6380);
    RedisConnectionManagerUtil.setServerTarget(outerManager, outerTarget);
    RedisConnectionManagerUtil.setServerTarget(innerManager, innerTarget);

    RedisServerTarget beforeOuter =
        currentServerTarget().set(RedisConnectionManagerUtil.getServerTarget(outerManager));
    try {
      RedisServerTarget beforeInner =
          currentServerTarget().set(RedisConnectionManagerUtil.getServerTarget(innerManager));
      try {
        assertThat(currentServerTarget().get()).isSameAs(innerTarget);
      } finally {
        currentServerTarget().restore(beforeInner);
      }

      assertThat(currentServerTarget().get()).isSameAs(outerTarget);
    } finally {
      currentServerTarget().restore(beforeOuter);
    }

    assertThat(currentServerTarget().get()).isNull();
  }

  @Test
  void restoresNestedRedisUris() {
    RedisURI outer = new RedisURI("redis://outer:6379");
    RedisURI inner = new RedisURI("redis://inner:6380");

    RedisURI beforeOuter = currentRedisUri().set(outer);
    try {
      RedisURI beforeInner = currentRedisUri().set(inner);
      try {
        assertThat(currentRedisUri().get()).isSameAs(inner);
      } finally {
        currentRedisUri().restore(beforeInner);
      }

      assertThat(currentRedisUri().get()).isSameAs(outer);
    } finally {
      currentRedisUri().restore(beforeOuter);
    }

    assertThat(currentRedisUri().get()).isNull();
  }
}
