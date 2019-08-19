package datadog.trace.agent.tooling;

import datadog.trace.bootstrap.WeakMap;

public class AgentTooling {
  private static final Cleaner CLEANER = new Cleaner();

  static {
    // WeakMap is used by other classes below, so we need to register the provider first.
    registerWeakMapProvider(CLEANER);
  }

  private static final DDLocationStrategy LOCATION_STRATEGY = new DDLocationStrategy();
  private static final DDCachingPoolStrategy POOL_STRATEGY = new DDCachingPoolStrategy(CLEANER);

  public static void init() {
    // Only need to trigger static initializers for now.
  }

  public static DDLocationStrategy locationStrategy() {
    return LOCATION_STRATEGY;
  }

  public static DDCachingPoolStrategy poolStrategy() {
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
