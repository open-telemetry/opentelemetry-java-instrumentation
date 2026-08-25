/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.geode.v1_4;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.geode.cache.client.Pool;
import org.apache.geode.cache.client.PoolFactory;

/**
 * Reads the target a client pool is being configured with, and hands it to the pool the factory
 * creates.
 *
 * <p>The factory is the only place where the configuration is complete and still untouched by what
 * the pool later learns from its locators, and a factory keeps its configuration between pools, so
 * every pool it creates is given a snapshot of its own.
 */
public final class GeodePoolInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("org.apache.geode.cache.client.PoolFactory");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("org.apache.geode.cache.client.PoolFactory"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("addServer").and(takesArguments(2)).and(takesArgument(0, String.class)),
        getClass().getName() + "$AddServerAdvice");
    transformer.applyAdviceToMethod(
        named("addLocator").and(takesArguments(2)).and(takesArgument(0, String.class)),
        getClass().getName() + "$AddLocatorAdvice");
    transformer.applyAdviceToMethod(
        named("setServerGroup").and(takesArguments(1)).and(takesArgument(0, String.class)),
        getClass().getName() + "$SetServerGroupAdvice");
    transformer.applyAdviceToMethod(
        named("reset").and(takesArguments(0)), getClass().getName() + "$ResetAdvice");
    transformer.applyAdviceToMethod(
        named("init")
            .and(takesArguments(1))
            .and(takesArgument(0, named("org.apache.geode.cache.client.Pool"))),
        getClass().getName() + "$InitAdvice");
    transformer.applyAdviceToMethod(
        named("create").and(takesArguments(1)).and(takesArgument(0, String.class)),
        getClass().getName() + "$CreateAdvice");
  }

  @SuppressWarnings("unused")
  public static class AddServerAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void captureConfiguredServer(
        @Advice.This PoolFactory poolFactory,
        @Advice.Argument(0) @Nullable String host,
        @Advice.Argument(1) int port) {
      GeodeServerTargets.addServer(poolFactory, host, port);
    }
  }

  @SuppressWarnings("unused")
  public static class AddLocatorAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void captureConfiguredLocator(
        @Advice.This PoolFactory poolFactory,
        @Advice.Argument(0) @Nullable String host,
        @Advice.Argument(1) int port) {
      GeodeServerTargets.addLocator(poolFactory, host, port);
    }
  }

  @SuppressWarnings("unused")
  public static class SetServerGroupAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void captureConfiguredServerGroup(
        @Advice.This PoolFactory poolFactory, @Advice.Argument(0) @Nullable String serverGroup) {
      GeodeServerTargets.setServerGroup(poolFactory, serverGroup);
    }
  }

  @SuppressWarnings("unused")
  public static class ResetAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void forgetConfiguration(@Advice.This PoolFactory poolFactory) {
      GeodeServerTargets.reset(poolFactory);
    }
  }

  @SuppressWarnings("unused")
  public static class InitAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void copyConfiguredTarget(
        @Advice.This PoolFactory poolFactory, @Advice.Argument(0) Pool sourcePool) {
      GeodeServerTargets.copyConfiguration(poolFactory, sourcePool);
    }
  }

  @SuppressWarnings("unused")
  public static class CreateAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void captureConfiguredTarget(
        @Advice.This PoolFactory poolFactory, @Advice.Return @Nullable Pool pool) {
      GeodeServerTargets.capture(poolFactory, pool);
    }
  }
}
