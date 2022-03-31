/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.muzzle;

import net.bytebuddy.agent.builder.AgentBuilder;

/**
 * This class contains class references for objects shared by the agent installer as well as muzzle
 * (both compile and runtime). Extracted out from AgentInstaller to begin separating some of the
 * logic out.
 */
public final class AgentTooling {

  private static volatile ClassLoader bootstrapProxy;

  public static AgentLocationStrategy locationStrategy() {
    return locationStrategy(null);
  }

  public static AgentLocationStrategy locationStrategy(ClassLoader bootstrapProxy) {
    return new AgentLocationStrategy(bootstrapProxy);
  }

  public static AgentBuilder.PoolStrategy poolStrategy() {
    return PoolStrategyHolder.POOL_STRATEGY;
  }

  public static void init(ClassLoader classLoader) {
    bootstrapProxy = classLoader;
  }

  private AgentTooling() {}

  private static class PoolStrategyHolder {
    private static final AgentBuilder.PoolStrategy POOL_STRATEGY =
        new AgentCachingPoolStrategy(locationStrategy(bootstrapProxy));
  }
}
