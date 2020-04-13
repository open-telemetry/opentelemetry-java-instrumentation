package datadog.trace.agent.tooling;

import datadog.trace.agent.tooling.bytebuddy.DDCachingPoolStrategy;
import datadog.trace.agent.tooling.bytebuddy.DDLocationStrategy;
import datadog.trace.bootstrap.WeakCache;
import datadog.trace.bootstrap.WeakCache.Provider;
import datadog.trace.bootstrap.WeakMap;
import java.util.Iterator;
import java.util.ServiceLoader;

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

  private static void registerWeakMapProvider() {
    if (!WeakMap.Provider.isProviderRegistered()) {
      WeakMap.Provider.registerIfAbsent(new WeakMapSuppliers.WeakConcurrent());
      //    WeakMap.Provider.registerIfAbsent(new WeakMapSuppliers.WeakConcurrent.Inline());
      //    WeakMap.Provider.registerIfAbsent(new WeakMapSuppliers.Guava());
    }
  }

  private static <K, V> Provider loadWeakCacheProvider() {
    final Iterator<Provider> providers =
        ServiceLoader.load(Provider.class, AgentInstaller.class.getClassLoader()).iterator();
    if (providers.hasNext()) {
      final Provider provider = providers.next();
      if (providers.hasNext()) {
        throw new IllegalStateException(
            "Only one implementation of WeakCache.Provider suppose to be in classpath");
      }
      return provider;
    }
    throw new IllegalStateException("Can't load implementation of WeakCache.Provider");
  }

  private static final Provider weakCacheProvider = loadWeakCacheProvider();

  private static final DDLocationStrategy LOCATION_STRATEGY = new DDLocationStrategy();
  private static final DDCachingPoolStrategy POOL_STRATEGY = new DDCachingPoolStrategy();

  public static <K, V> WeakCache<K, V> newWeakCache() {
    return weakCacheProvider.newWeakCache();
  }

  public static <K, V> WeakCache<K, V> newWeakCache(final long maxSize) {
    return weakCacheProvider.newWeakCache(maxSize);
  }

  public static DDLocationStrategy locationStrategy() {
    return LOCATION_STRATEGY;
  }

  public static DDCachingPoolStrategy poolStrategy() {
    return POOL_STRATEGY;
  }
}
