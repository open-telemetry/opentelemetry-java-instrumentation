package io.opentelemetry.auto.tooling;

import io.opentelemetry.auto.bootstrap.WeakMap;

/**
 * This class contains class references for objects shared by the agent installer as well as muzzle
 * (both compile and runtime). Extracted out from AgentInstaller to begin separating some of the
 * logic out.
 */
public class AgentTooling {
  private static final Cleaner CLEANER = new Cleaner();

  static {
    // WeakMap is used by other classes below, so we need to register the provider first.
    registerWeakMapProvider(CLEANER);
  }

  private static final AgentLocationStrategy LOCATION_STRATEGY = new AgentLocationStrategy();
  private static final AgentCachingPoolStrategy POOL_STRATEGY =
      new AgentCachingPoolStrategy(CLEANER);

  public static void init() {
    // Only need to trigger static initializers for now.
  }

  public static AgentLocationStrategy locationStrategy() {
    return LOCATION_STRATEGY;
  }

  public static AgentCachingPoolStrategy poolStrategy() {
    return POOL_STRATEGY;
  }

  private static void registerWeakMapProvider(final Cleaner cleaner) {
    if (!WeakMap.Provider.isProviderRegistered()) {
      WeakMap.Provider.registerIfAbsent(new WeakMapSuppliers.WeakConcurrent(cleaner));
      //    WeakMap.Provider.registerIfAbsent(new WeakMapSuppliers.WeakConcurrent.Inline());
      //    WeakMap.Provider.registerIfAbsent(new WeakMapSuppliers.Guava());
    }
  }
}
