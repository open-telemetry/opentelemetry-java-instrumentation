/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.muzzle;

import java.util.Iterator;
import java.util.ServiceLoader;
import net.bytebuddy.agent.builder.AgentBuilder;

/**
 * This class contains class references for objects shared by the agent installer as well as muzzle
 * (both compile and runtime). Extracted out from AgentInstaller to begin separating some of the
 * logic out.
 */
public final class AgentTooling {

  private static final AgentLocationStrategy LOCATION_STRATEGY =
      locationStrategy(getBootstrapProxy());

  private static final AgentBuilder.PoolStrategy POOL_STRATEGY =
      new AgentCachingPoolStrategy(LOCATION_STRATEGY);

  public static AgentLocationStrategy locationStrategy() {
    return LOCATION_STRATEGY;
  }

  public static AgentLocationStrategy locationStrategy(ClassLoader bootstrapProxy) {
    return new AgentLocationStrategy(bootstrapProxy);
  }

  public static AgentBuilder.PoolStrategy poolStrategy() {
    return POOL_STRATEGY;
  }

  private static ClassLoader getBootstrapProxy() {
    Iterator<BootstrapProxyProvider> iterator =
        ServiceLoader.load(BootstrapProxyProvider.class).iterator();
    if (iterator.hasNext()) {
      BootstrapProxyProvider bootstrapProxyProvider = iterator.next();
      return bootstrapProxyProvider.getBootstrapProxy();
    }

    return null;
  }

  private AgentTooling() {}
}
