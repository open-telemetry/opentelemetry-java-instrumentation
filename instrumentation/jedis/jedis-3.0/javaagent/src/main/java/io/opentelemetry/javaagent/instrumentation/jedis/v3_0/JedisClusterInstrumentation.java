/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v3_0;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.isDeclaredBy;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.Set;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisClusterConnectionHandler;

class JedisClusterInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return namedOneOf(
        "redis.clients.jedis.JedisClusterConnectionHandler",
        "redis.clients.jedis.JedisSlotBasedConnectionHandler");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isConstructor()
            .and(isDeclaredBy(named("redis.clients.jedis.JedisClusterConnectionHandler")))
            .and(takesArgument(0, named("java.util.Set"))),
        getClass().getName() + "$ConstructorAdvice");
    transformer.applyAdviceToMethod(
        named("initializeSlotsCache").and(takesArgument(0, named("java.util.Set"))),
        getClass().getName() + "$InitializeAdvice");
    transformer.applyAdviceToMethod(
        namedOneOf("getConnection", "getConnectionFromSlot")
            .and(isDeclaredBy(named("redis.clients.jedis.JedisSlotBasedConnectionHandler")))
            .and(returns(named("redis.clients.jedis.Jedis"))),
        getClass().getName() + "$GetConnectionAdvice");
    transformer.applyAdviceToMethod(
        named("getConnectionFromNode")
            .and(isDeclaredBy(named("redis.clients.jedis.JedisClusterConnectionHandler")))
            .and(returns(named("redis.clients.jedis.Jedis"))),
        getClass().getName() + "$GetConnectionAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConstructorAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Scope onEnter(@Advice.Argument(0) @Nullable Set<HostAndPort> nodes) {
      return JedisSingletons.openConfiguredTargetScope(JedisServerTargets.ofNodes(nodes));
    }

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This JedisClusterConnectionHandler handler,
        @Advice.Argument(0) @Nullable Set<HostAndPort> nodes,
        @Advice.Enter @Nullable Scope scope) {
      try {
        JedisSingletons.setClusterTarget(handler, JedisServerTargets.ofNodes(nodes));
      } finally {
        if (scope != null) {
          scope.close();
        }
      }
    }
  }

  @SuppressWarnings("unused")
  public static class InitializeAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Scope onEnter(@Advice.Argument(0) @Nullable Set<HostAndPort> nodes) {
      return JedisSingletons.openConfiguredTargetScope(JedisServerTargets.ofNodes(nodes));
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.Enter @Nullable Scope scope) {
      if (scope != null) {
        scope.close();
      }
    }
  }

  @SuppressWarnings("unused")
  public static class GetConnectionAdvice {

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Scope onEnter(@Advice.This JedisClusterConnectionHandler handler) {
      return JedisSingletons.openClusterTargetScope(handler);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This JedisClusterConnectionHandler handler,
        @Advice.Return @Nullable Object connection,
        @Advice.Enter @Nullable Scope scope) {
      try {
        JedisSingletons.attachClusterTarget(handler, connection);
      } finally {
        if (scope != null) {
          scope.close();
        }
      }
    }
  }
}
