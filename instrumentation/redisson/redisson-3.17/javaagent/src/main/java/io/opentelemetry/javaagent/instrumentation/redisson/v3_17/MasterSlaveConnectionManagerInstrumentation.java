/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redisson.v3_17;

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
import org.redisson.config.ConfigServerTargetUtil317;
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
    // redisson 3.20 through 3.27 hand the manager the service manager the configuration lives in
    transformer.applyAdviceToMethod(
        isConstructor().and(takesArgument(1, named("org.redisson.connection.ServiceManager"))),
        getClass().getName() + "$ServiceManagerConstructorAdvice");
    transformer.applyAdviceToMethod(
        named("createClient").and(returns(named("org.redisson.client.RedisClient"))),
        getClass().getName() + "$CreateClientAdvice");
  }

  public static class ConfigArgument0ConstructorAdvice {

    @SuppressWarnings("unused")
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This MasterSlaveConnectionManager manager,
        @Advice.Argument(0) @Nullable Config config) {
      RedissonServerTargets.capture(manager, ConfigServerTargetUtil317.of(config));
    }
  }

  public static class ConfigArgument1ConstructorAdvice {

    @SuppressWarnings("unused")
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This MasterSlaveConnectionManager manager,
        @Advice.Argument(1) @Nullable Config config) {
      RedissonServerTargets.capture(manager, ConfigServerTargetUtil317.of(config));
    }
  }

  public static class ServiceManagerConstructorAdvice {

    @SuppressWarnings("unused")
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This MasterSlaveConnectionManager manager,
        @Advice.Argument(1) @Nullable Object serviceManager) {
      RedissonServerTargets.capture(
          manager, ConfigServerTargetUtil317.ofServiceManager(serviceManager));
    }
  }

  public static class CreateClientAdvice {

    @SuppressWarnings("unused")
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This MasterSlaveConnectionManager manager,
        @Advice.Return @Nullable RedisClient client) {
      RedissonServerTargets.store(manager, client);
    }
  }
}
