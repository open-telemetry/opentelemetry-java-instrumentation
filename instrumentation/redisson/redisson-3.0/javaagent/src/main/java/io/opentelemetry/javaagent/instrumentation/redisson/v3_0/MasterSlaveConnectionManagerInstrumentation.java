/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redisson.v3_0;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.redisson.common.v3_0.RedissonServerTargets;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.redisson.client.RedisClient;
import org.redisson.config.Config;
import org.redisson.config.ConfigServerTargetBefore317;
import org.redisson.connection.MasterSlaveConnectionManager;

class MasterSlaveConnectionManagerInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.redisson.connection.MasterSlaveConnectionManager");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    // Config is mutable, so capture an immutable target when the manager is constructed.
    transformer.applyAdviceToMethod(
        isConstructor().and(takesArgument(0, named("org.redisson.config.Config"))),
        getClass().getName() + "$ConfigArgument0ConstructorAdvice");
    transformer.applyAdviceToMethod(
        isConstructor().and(takesArgument(1, named("org.redisson.config.Config"))),
        getClass().getName() + "$ConfigArgument1ConstructorAdvice");
    transformer.applyAdviceToMethod(
        named("createClient").and(returns(named("org.redisson.client.RedisClient"))),
        getClass().getName() + "$CreateClientAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConfigArgument0ConstructorAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This MasterSlaveConnectionManager manager,
        @Advice.Argument(0) @Nullable Config config) {
      RedissonServerTargets.capture(manager, ConfigServerTargetBefore317.of(config));
    }
  }

  @SuppressWarnings("unused")
  public static class ConfigArgument1ConstructorAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This MasterSlaveConnectionManager manager,
        @Advice.Argument(1) @Nullable Config config) {
      RedissonServerTargets.capture(manager, ConfigServerTargetBefore317.of(config));
    }
  }

  @SuppressWarnings("unused")
  public static class CreateClientAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This MasterSlaveConnectionManager manager,
        @Advice.Return @Nullable RedisClient client) {
      RedissonServerTargets.copy(manager, client);
    }
  }
}
