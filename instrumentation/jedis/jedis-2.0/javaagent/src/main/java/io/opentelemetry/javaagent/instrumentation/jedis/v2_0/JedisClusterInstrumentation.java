/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v2_0;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.Set;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Captures the nodes a cluster client was configured with and hands them to the connection the
 * cluster picks for a command, so that a command is reported against the cluster rather than the
 * node that happens to own the slot.
 *
 * <p>The slot based handler is the only handler that picks a connection: its superclass declares
 * both picking methods as abstract. Cluster support was added in jedis 2.4, so on earlier versions
 * this matches nothing. The advice names no cluster type, because {@code HostAndPort} only arrived
 * with the cluster and the module also covers the versions before it.
 */
class JedisClusterInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("redis.clients.jedis.JedisSlotBasedConnectionHandler");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isConstructor().and(takesArgument(0, named("java.util.Set"))),
        getClass().getName() + "$ConstructorAdvice");
    transformer.applyAdviceToMethod(
        namedOneOf("getConnection", "getConnectionFromSlot")
            .and(returns(named("redis.clients.jedis.Jedis"))),
        getClass().getName() + "$GetConnectionAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConstructorAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This Object handler, @Advice.Argument(0) @Nullable Set<?> nodes) {
      JedisSingletons.setClusterTarget(handler, JedisServerTargets.ofNodes(nodes));
    }
  }

  @SuppressWarnings("unused")
  public static class GetConnectionAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This Object handler, @Advice.Return @Nullable Object connection) {
      JedisSingletons.attachClusterTarget(handler, connection);
    }
  }
}
