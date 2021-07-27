/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.muzzle;

/**
 * This class contains class references for objects shared by the agent installer as well as muzzle
 * (both compile and runtime). Extracted out from AgentInstaller to begin separating some of the
 * logic out.
 */
public final class AgentTooling {

  private static final AgentCachingPoolStrategy POOL_STRATEGY = new AgentCachingPoolStrategy();

  public static AgentLocationStrategy locationStrategy() {
    return locationStrategy(null);
  }

  public static AgentLocationStrategy locationStrategy(ClassLoader bootstrapProxy) {
    return new AgentLocationStrategy(bootstrapProxy);
  }

  public static AgentCachingPoolStrategy poolStrategy() {
    return POOL_STRATEGY;
  }

  private AgentTooling() {}
}
