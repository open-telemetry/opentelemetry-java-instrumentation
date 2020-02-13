package io.opentelemetry.auto.tooling;

import io.opentelemetry.auto.bootstrap.WeakMap;

/**
 * This class contains class references for objects shared by the agent installer as well as muzzle
 * (both compile and runtime). Extracted out from AgentInstaller to begin separating some of the
 * logic out.
 */
public class AgentTooling {

  static {
    // WeakMap is used by other classes below, so we need to register the provider first.
    registerWeakMapProvider();
  }

  private static final AgentLocationStrategy LOCATION_STRATEGY = new AgentLocationStrategy();
  private static final AgentCachingPoolStrategy POOL_STRATEGY = new AgentCachingPoolStrategy();

  public static AgentLocationStrategy locationStrategy() {
    return LOCATION_STRATEGY;
  }

  public static AgentCachingPoolStrategy poolStrategy() {
    return POOL_STRATEGY;
  }

  private static void registerWeakMapProvider() {
    if (!WeakMap.Provider.isProviderRegistered()) {
      WeakMap.Provider.registerIfAbsent(new WeakMapSuppliers.WeakConcurrent(new Cleaner()));
      //    WeakMap.Provider.registerIfAbsent(new WeakMapSuppliers.WeakConcurrent.Inline());
      //    WeakMap.Provider.registerIfAbsent(new WeakMapSuppliers.Guava());
    }
  }
}
