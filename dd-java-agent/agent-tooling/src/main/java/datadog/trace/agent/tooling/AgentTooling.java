package datadog.trace.agent.tooling;

import datadog.trace.bootstrap.WeakMap;

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

  private static final DDLocationStrategy LOCATION_STRATEGY = new DDLocationStrategy();
  private static final DDCachingPoolStrategy POOL_STRATEGY = new DDCachingPoolStrategy();

  public static DDLocationStrategy locationStrategy() {
    return LOCATION_STRATEGY;
  }

  public static DDCachingPoolStrategy poolStrategy() {
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
