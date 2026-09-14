/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v4_0;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.isDeclaredBy;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisClusterInfoCache;
import redis.clients.jedis.providers.ConnectionProvider;

class JedisConnectionProviderInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return namedOneOf(
        "redis.clients.jedis.providers.ClusterConnectionProvider",
        "redis.clients.jedis.providers.ShardedConnectionProvider",
        // 4.4 and later
        "redis.clients.jedis.providers.SentineledConnectionProvider",
        "redis.clients.jedis.providers.SentineledConnectionProvider$SentinelListener",
        "redis.clients.jedis.JedisClusterInfoCache",
        "redis.clients.jedis.JedisClusterInfoCache$TopologyRefreshTask");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isConstructor()
            .and(takesArguments(4))
            .and(takesArgument(0, named("redis.clients.jedis.JedisClientConfig")))
            .and(takesArgument(1, named("org.apache.commons.pool2.impl.GenericObjectPoolConfig")))
            .and(takesArgument(2, Set.class))
            .and(takesArgument(3, named("java.time.Duration"))),
        getClass().getName() + "$InitializeTopologyRefresh51Advice");
    transformer.applyAdviceToMethod(
        isConstructor()
            .and(takesArguments(5))
            .and(takesArgument(0, named("redis.clients.jedis.JedisClientConfig")))
            .and(takesArgument(1, named("redis.clients.jedis.csc.Cache")))
            .and(takesArgument(2, named("org.apache.commons.pool2.impl.GenericObjectPoolConfig")))
            .and(takesArgument(3, Set.class))
            .and(takesArgument(4, named("java.time.Duration"))),
        getClass().getName() + "$InitializeTopologyRefresh52Advice");
    transformer.applyAdviceToMethod(
        isConstructor()
            .and(takesArguments(1))
            .and(takesArgument(0, named("redis.clients.jedis.JedisClusterInfoCache")))
            .and(
                isDeclaredBy(
                    named("redis.clients.jedis.JedisClusterInfoCache$TopologyRefreshTask"))),
        getClass().getName() + "$InitializeTopologyRefreshTaskAdvice");
    transformer.applyAdviceToMethod(
        named("initializeSlotsCache").and(takesArgument(0, Set.class)),
        getClass().getName() + "$InitializeClusterAdvice");
    transformer.applyAdviceToMethod(
        named("initialize").and(takesArgument(0, List.class)),
        getClass().getName() + "$InitializeShardsAdvice");
    transformer.applyAdviceToMethod(
        named("initSentinels").and(takesArgument(0, Set.class)),
        getClass().getName() + "$InitializeSentinelsAdvice");
    transformer.applyAdviceToMethod(
        named("run")
            // TopologyRefreshTask has the same run() signature; keep this advice on the listener.
            .and(
                isDeclaredBy(
                    named(
                        "redis.clients.jedis.providers.SentineledConnectionProvider$SentinelListener"))),
        getClass().getName() + "$SentinelListenerAdvice");
    transformer.applyAdviceToMethod(
        named("run")
            .and(
                isDeclaredBy(
                    named("redis.clients.jedis.JedisClusterInfoCache$TopologyRefreshTask"))),
        getClass().getName() + "$TopologyRefreshAdvice");
    transformer.applyAdviceToMethod(
        namedOneOf(
                "getConnection",
                "getConnectionFromSlot",
                "getReplicaConnection",
                "getReplicaConnectionFromSlot")
            .and(returns(named("redis.clients.jedis.Connection"))),
        getClass().getName() + "$ProviderTargetScopeAdvice");
    transformer.applyAdviceToMethod(
        named("initMaster").and(takesArgument(0, named("redis.clients.jedis.HostAndPort"))),
        getClass().getName() + "$ProviderTargetScopeAdvice");
    transformer.applyAdviceToMethod(
        named("renewSlotCache"), getClass().getName() + "$ProviderTargetScopeAdvice");
  }

  @SuppressWarnings("unused")
  public static class InitializeTopologyRefresh51Advice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static void onEnter(@Advice.Argument(2) Set<HostAndPort> nodes) {
      JedisConfiguredTargets.beginTopologyTargetInitialization(nodes);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit() {
      JedisConfiguredTargets.endTopologyTargetInitialization();
    }
  }

  @SuppressWarnings("unused")
  public static class InitializeTopologyRefresh52Advice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static void onEnter(@Advice.Argument(3) Set<HostAndPort> nodes) {
      JedisConfiguredTargets.beginTopologyTargetInitialization(nodes);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit() {
      JedisConfiguredTargets.endTopologyTargetInitialization();
    }
  }

  @SuppressWarnings("unused")
  public static class InitializeTopologyRefreshTaskAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.Argument(0) JedisClusterInfoCache cache) {
      JedisConfiguredTargets.initializePendingTopologyTarget(cache);
    }
  }

  @SuppressWarnings("unused")
  public static class InitializeClusterAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Scope onEnter(
        @Advice.This ConnectionProvider provider,
        @Advice.FieldValue("cache") @Nullable JedisClusterInfoCache cache,
        @Advice.Argument(0) @Nullable Set<HostAndPort> nodes) {
      RedisServerTarget target = JedisServerTarget.ofNodes(nodes);
      JedisConfiguredTargets.setProviderTarget(provider, target);
      JedisConfiguredTargets.setTopologyTarget(cache, target);
      return JedisConfiguredTargets.configuredTargetContext(target).makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.Enter @Nullable Scope scope) {
      if (scope != null) {
        scope.close();
      }
    }
  }

  @SuppressWarnings("unused")
  public static class InitializeShardsAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Scope onEnter(
        @Advice.This ConnectionProvider provider,
        @Advice.Argument(0) @Nullable List<HostAndPort> shards) {
      RedisServerTarget target = JedisServerTarget.ofShards(shards);
      JedisConfiguredTargets.setProviderTarget(provider, target);
      return JedisConfiguredTargets.configuredTargetContext(target).makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.Enter @Nullable Scope scope) {
      if (scope != null) {
        scope.close();
      }
    }
  }

  @SuppressWarnings("unused")
  public static class InitializeSentinelsAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Scope onEnter(
        @Advice.This ConnectionProvider provider,
        @Advice.FieldValue("masterName") @Nullable String masterName,
        @Advice.Argument(0) @Nullable Set<HostAndPort> sentinels) {
      RedisServerTarget target = JedisServerTarget.ofSentinels(masterName, sentinels);
      JedisConfiguredTargets.setProviderTarget(provider, target);
      return JedisConfiguredTargets.configuredTargetContext(target).makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.Enter @Nullable Scope scope) {
      if (scope != null) {
        scope.close();
      }
    }
  }

  @SuppressWarnings("unused")
  public static class SentinelListenerAdvice {

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Scope onEnter(@Advice.FieldValue("this$0") ConnectionProvider provider) {
      Context context = JedisConfiguredTargets.providerTargetContext(provider);
      return context != null ? context.makeCurrent() : null;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.Enter @Nullable Scope scope) {
      if (scope != null) {
        scope.close();
      }
    }
  }

  @SuppressWarnings("unused")
  public static class TopologyRefreshAdvice {

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Scope onEnter(@Advice.FieldValue("this$0") JedisClusterInfoCache cache) {
      Context context = JedisConfiguredTargets.topologyTargetContext(cache);
      return context != null ? context.makeCurrent() : null;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.Enter @Nullable Scope scope) {
      if (scope != null) {
        scope.close();
      }
    }
  }

  @SuppressWarnings("unused")
  public static class ProviderTargetScopeAdvice {

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Scope onEnter(@Advice.This ConnectionProvider provider) {
      Context context = JedisConfiguredTargets.providerTargetContext(provider);
      return context != null ? context.makeCurrent() : null;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.Enter @Nullable Scope scope) {
      if (scope != null) {
        scope.close();
      }
    }
  }
}
