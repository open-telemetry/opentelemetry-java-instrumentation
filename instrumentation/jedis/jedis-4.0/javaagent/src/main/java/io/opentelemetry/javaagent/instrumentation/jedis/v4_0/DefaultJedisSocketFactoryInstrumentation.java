/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v4_0;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisSocketFactory;

class DefaultJedisSocketFactoryInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("redis.clients.jedis.DefaultJedisSocketFactory");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isConstructor().and(takesArgument(0, String.class)).and(takesArgument(1, int.class)),
        getClass().getName() + "$HostAndPortPartsAdvice");
    transformer.applyAdviceToMethod(
        isConstructor().and(takesArgument(0, named("redis.clients.jedis.HostAndPort"))),
        getClass().getName() + "$HostAndPortAdvice");
    transformer.applyAdviceToMethod(
        named("updateHostAndPort")
            .and(takesArguments(1))
            .and(takesArgument(0, named("redis.clients.jedis.HostAndPort"))),
        getClass().getName() + "$HostAndPortAdvice");
  }

  @SuppressWarnings("unused")
  public static class HostAndPortPartsAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This JedisSocketFactory socketFactory,
        @Advice.Argument(0) String host,
        @Advice.Argument(1) int port) {
      JedisSocketFactoryInfo.setConfiguredHostAndPort(socketFactory, new HostAndPort(host, port));
    }
  }

  @SuppressWarnings("unused")
  public static class HostAndPortAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This JedisSocketFactory socketFactory,
        @Advice.Argument(0) HostAndPort hostAndPort) {
      JedisSocketFactoryInfo.setConfiguredHostAndPort(socketFactory, hostAndPort);
    }
  }
}
