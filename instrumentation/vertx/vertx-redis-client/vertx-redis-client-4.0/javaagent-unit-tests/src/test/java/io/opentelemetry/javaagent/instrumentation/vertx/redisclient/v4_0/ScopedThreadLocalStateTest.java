/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.redisclient.v4_0;

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
        RedisConnectionManagerUtil.setServerTargetThreadLocal(outerManager);
    try {
      RedisServerTarget beforeInner =
          RedisConnectionManagerUtil.setServerTargetThreadLocal(innerManager);
      try {
        assertThat(RedisConnectionManagerUtil.getServerTargetThreadLocal()).isSameAs(innerTarget);
      } finally {
        RedisConnectionManagerUtil.restoreServerTargetThreadLocal(beforeInner);
      }

      assertThat(RedisConnectionManagerUtil.getServerTargetThreadLocal()).isSameAs(outerTarget);
    } finally {
      RedisConnectionManagerUtil.restoreServerTargetThreadLocal(beforeOuter);
    }

    assertThat(RedisConnectionManagerUtil.getServerTargetThreadLocal()).isNull();
  }

  @Test
  void restoresNestedRedisUris() {
    RedisURI outer = new RedisURI("redis://outer:6379");
    RedisURI inner = new RedisURI("redis://inner:6380");

    RedisURI beforeOuter = VertxRedisClientSingletons.setRedisUriThreadLocal(outer);
    try {
      RedisURI beforeInner = VertxRedisClientSingletons.setRedisUriThreadLocal(inner);
      try {
        assertThat(VertxRedisClientSingletons.getRedisUriThreadLocal()).isSameAs(inner);
      } finally {
        VertxRedisClientSingletons.restoreRedisUriThreadLocal(beforeInner);
      }

      assertThat(VertxRedisClientSingletons.getRedisUriThreadLocal()).isSameAs(outer);
    } finally {
      VertxRedisClientSingletons.restoreRedisUriThreadLocal(beforeOuter);
    }

    assertThat(VertxRedisClientSingletons.getRedisUriThreadLocal()).isNull();
  }
}
