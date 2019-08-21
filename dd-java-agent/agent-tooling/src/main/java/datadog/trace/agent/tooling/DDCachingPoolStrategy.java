package datadog.trace.agent.tooling;

import static datadog.trace.agent.tooling.ClassLoaderMatcher.BOOTSTRAP_CLASSLOADER;
import static net.bytebuddy.agent.builder.AgentBuilder.PoolStrategy;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import datadog.trace.bootstrap.WeakMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.pool.TypePool;

/**
 * Custom Pool strategy.
 *
 * <p>Here we are using WeakMap.Provider as the backing ClassLoader -> CacheProvider lookup.
 *
 * <p>We also use our bootstrap proxy when matching against the bootstrap loader.
 *
 * <p>The CacheProvider is a custom implementation that uses guava's cache to expire and limit size.
 *
 * <p>By evicting from the cache we are able to reduce the memory overhead of the agent for apps
 * that have many classes.
 *
 * <p>See eviction policy below.
 */
public class DDCachingPoolStrategy implements PoolStrategy {
  private final WeakMap<ClassLoader, TypePool.CacheProvider> typePoolCache =
      WeakMap.Provider.newWeakMap();
  private final Cleaner cleaner;

  public DDCachingPoolStrategy(final Cleaner cleaner) {
    this.cleaner = cleaner;
  }

  @Override
  public TypePool typePool(final ClassFileLocator classFileLocator, final ClassLoader classLoader) {
    final ClassLoader key =
        BOOTSTRAP_CLASSLOADER == classLoader ? Utils.getBootstrapProxy() : classLoader;
    TypePool.CacheProvider cache = typePoolCache.get(key);
    if (null == cache) {
      synchronized (key) {
        cache = typePoolCache.get(key);
        if (null == cache) {
          cache = EvictingCacheProvider.withObjectType(cleaner, 1, TimeUnit.MINUTES);
          typePoolCache.put(key, cache);
        }
      }
    }
    return new TypePool.Default.WithLazyResolution(
        cache, classFileLocator, TypePool.Default.ReaderMode.FAST);
  }

  private static class EvictingCacheProvider implements TypePool.CacheProvider {

    /** A map containing all cached resolutions by their names. */
    private final Cache<String, TypePool.Resolution> cache;

    /** Creates a new simple cache. */
    private EvictingCacheProvider(
        final Cleaner cleaner, final long expireDuration, final TimeUnit unit) {
      cache =
          CacheBuilder.newBuilder()
              .initialCapacity(1000)
              .maximumSize(10000)
              .expireAfterAccess(expireDuration, unit)
              .build();

      /*
       * The cache only does cleanup on occasional reads and writes.
       * We want to ensure this happens more regularly, so we schedule a thread to do run cleanup manually.
       */
      cleaner.scheduleCleaning(cache, CacheCleaner.CLEANER, expireDuration, unit);
    }

    private static EvictingCacheProvider withObjectType(
        final Cleaner cleaner, final long expireDuration, final TimeUnit unit) {
      final EvictingCacheProvider cacheProvider =
          new EvictingCacheProvider(cleaner, expireDuration, unit);
      cacheProvider.register(
          Object.class.getName(), new TypePool.Resolution.Simple(TypeDescription.OBJECT));
      return cacheProvider;
    }

    @Override
    public TypePool.Resolution find(final String name) {
      return cache.getIfPresent(name);
    }

    @Override
    public TypePool.Resolution register(final String name, final TypePool.Resolution resolution) {
      try {
        return cache.get(name, new ResolutionProvider(resolution));
      } catch (final ExecutionException e) {
        return resolution;
      }
    }

    @Override
    public void clear() {
      cache.invalidateAll();
    }

    public long size() {
      return cache.size();
    }

    private static class CacheCleaner implements Cleaner.Adapter<Cache> {
      private static final CacheCleaner CLEANER = new CacheCleaner();

      @Override
      public void clean(final Cache target) {
        target.cleanUp();
      }
    }

    private static class ResolutionProvider implements Callable<TypePool.Resolution> {
      private final TypePool.Resolution value;

      private ResolutionProvider(final TypePool.Resolution value) {
        this.value = value;
      }

      @Override
      public TypePool.Resolution call() {
        return value;
      }
    }
  }
}
