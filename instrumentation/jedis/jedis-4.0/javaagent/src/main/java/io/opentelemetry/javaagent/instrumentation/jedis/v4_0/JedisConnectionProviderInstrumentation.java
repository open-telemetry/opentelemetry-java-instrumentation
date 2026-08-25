/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v4_0;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import redis.clients.jedis.Connection;
import redis.clients.jedis.HostAndPort;

/**
 * Captures the endpoints a cluster, sharded or Sentinel backed client was configured with and hands
 * them to every connection the client's provider produces.
 *
 * <p>The provider interface was renamed between jedis 4.0.0-beta1 and 4.0.0, and the sentineled
 * provider only arrived in 4.4, so both spellings are matched by name and no advice names a
 * provider type.
 */
class JedisConnectionProviderInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return namedOneOf(
        // 4.0.0-beta1
        "redis.clients.jedis.providers.JedisClusterConnectionProvider",
        // 4.0.0 and later
        "redis.clients.jedis.providers.ClusterConnectionProvider",
        "redis.clients.jedis.providers.ShardedConnectionProvider",
        // 4.4 and later
        "redis.clients.jedis.providers.SentineledConnectionProvider");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isConstructor().and(takesArgument(0, named("java.util.Set"))),
        getClass().getName() + "$ClusterConstructorAdvice");
    transformer.applyAdviceToMethod(
        isConstructor().and(takesArgument(0, named("java.util.List"))),
        getClass().getName() + "$ShardedConstructorAdvice");
    transformer.applyAdviceToMethod(
        isConstructor().and(takesArgument(0, named("java.lang.String"))),
        getClass().getName() + "$SentineledConstructorAdvice");
    transformer.applyAdviceToMethod(
        namedOneOf("getConnection", "getConnectionFromSlot")
            .and(returns(named("redis.clients.jedis.Connection"))),
        getClass().getName() + "$GetConnectionAdvice");
  }

  @SuppressWarnings("unused")
  public static class ClusterConstructorAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This Object provider, @Advice.Argument(0) @Nullable Set<HostAndPort> nodes) {
      JedisSingletons.setProviderTarget(provider, JedisServerTargets.ofNodes(nodes));
    }
  }

  @SuppressWarnings("unused")
  public static class ShardedConstructorAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This Object provider, @Advice.Argument(0) @Nullable List<HostAndPort> shards) {
      JedisSingletons.setProviderTarget(provider, JedisServerTargets.ofNodes(shards));
    }
  }

  @SuppressWarnings("unused")
  public static class SentineledConstructorAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This Object provider, @Advice.Argument(0) @Nullable String masterName) {
      JedisSingletons.setProviderTarget(provider, RedisServerTarget.ofLogicalName(masterName));
    }
  }

  @SuppressWarnings("unused")
  public static class GetConnectionAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This Object provider, @Advice.Return @Nullable Connection connection) {
      JedisSingletons.attachProviderTarget(provider, connection);
    }
  }
}
