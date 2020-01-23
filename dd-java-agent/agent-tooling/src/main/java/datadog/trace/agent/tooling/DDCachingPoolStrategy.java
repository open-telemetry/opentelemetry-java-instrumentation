package datadog.trace.agent.tooling;

import static net.bytebuddy.agent.builder.AgentBuilder.PoolStrategy;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.pool.TypePool;

/**
 * NEW (Jan 2020) Custom Pool strategy.
 *
 * <ul>
 *   Uses a Guava Cache directly...
 *   <li>better control over locking than WeakMap.Provider
 *   <li>provides direct control over concurrency level
 *   <li>initial and maximum capacity
 * </ul>
 *
 * <ul>
 *   There two core parts to the cache...
 *   <li>a cache of ID assignments for ClassLoaders
 *   <li>a single cache of TypeResolutions for all ClassLoaders - keyed by a custom composite key
 *       that combines loader ID & name
 * </ul>
 *
 * <p>This design was chosen to create a single limited size cache that can be adjusted
 * for the entire application -- without having to create a large number of WeakReference objects.
 *
 * <p>The ID assignment mostly assigns a single ID to each ClassLoader, but the maximumSize
 * restriction means that an evicted ClassLoader could be assigned another ID later on.
 *
 * <p>For the validity of the cache, the important part is that ID assignment guarantees that
 * no two ClassLoaders share the same ID.
 *
 * <p>NOTE: As an additional safe-guard, a new CacheInstance can be created if the original loader ID
 * sequence is exhausted.
 */
@Slf4j
public class DDCachingPoolStrategy implements PoolStrategy {
  /**
   * Most of the logic exists in CacheInstance This volatile + exhaustion checking is defense
   * against loader ID exhaustion
   */
  volatile CacheInstance cacheInstance = new CacheInstance();

  @Override
  public TypePool typePool(final ClassFileLocator classFileLocator, final ClassLoader classLoader) {
    CacheInstance cacheInstance = this.cacheInstance;

    TypePool typePool = cacheInstance.typePool(classFileLocator, classLoader);
    if (cacheInstance.exhaustedLoaderIdSeq()) {
      // If the loader ID sequence is exhausted, drop the prior cache & start over
      // The ID space is so large that this shouldn't occur
      log.error("cacheInstance exhausted - rebuilding cache");

      this.cacheInstance = new CacheInstance();
    }
    return typePool;
  }

  /*
   * CacheInstance embodies the core of the cache.  In general, we only
   * expect a single CacheInstance object to ever be created.
   *
   * However, CacheInstance does provide an extra layer of protection
   * against loaderIdSeq exhaustion.  If ever the loaderIdSeq of
   * CacheInstance is exhausted, then DDCachingPoolStrategy.typePool
   * will detect that and discard the CacheInstance.
   *
   * At that time, a new CacheInstance with a fresh sequence will
   * be created in its place.
   */
  private static final class CacheInstance {
    static final int CONCURRENCY_LEVEL = 8;
    static final int LOADER_CAPACITY = 64;
    static final int TYPE_CAPACITY = 64;

    static final long BOOTSTRAP_ID = Long.MIN_VALUE;
    static final long START_ID = BOOTSTRAP_ID + 1;
    static final long LIMIT_ID = Long.MAX_VALUE - 10;

    static final long EXHAUSTED_ID = LIMIT_ID;

    // Many things are package visible for testing purposes --
    // others to avoid creation of synthetic accessors

    /**
     * Cache of recent loaderIds: guarantee is that no two loaders are given the same ID; however, a
     * loader may be given more than one ID if it falls out the cache.
     */
    final Cache<ClassLoader, Long> loaderIdCache =
        CacheBuilder.newBuilder()
            .weakKeys()
            .concurrencyLevel(CONCURRENCY_LEVEL)
            .initialCapacity(LOADER_CAPACITY / 2)
            .maximumSize(LOADER_CAPACITY)
            .build();

    /**
     * Single shared Type.Resolution cache -- uses a composite key of loader ID & class name The
     * initial capacity is set to the maximum capacity to avoid expansion overhead.
     */
    final Cache<TypeCacheKey, TypePool.Resolution> sharedResolutionCache =
        CacheBuilder.newBuilder()
            .softValues()
            .concurrencyLevel(CONCURRENCY_LEVEL)
            .initialCapacity(TYPE_CAPACITY)
            .maximumSize(TYPE_CAPACITY)
            .build();

    /**
     * ID sequence for loaders -- BOOTSTRAP_ID is reserved -- starts higher at START_ID Sequence
     * proceeds up until LIMIT_ID at which the sequence and this cacheInstance are considered to be
     * exhausted
     */
    final AtomicLong loaderIdSeq = new AtomicLong(START_ID);

    /** Fast path for bootstrap */
    final SharedResolutionCacheAdapter bootstrapCacheProvider =
        new SharedResolutionCacheAdapter(BOOTSTRAP_ID, sharedResolutionCache);

    private final Callable<Long> provisionIdCallable =
        new Callable<Long>() {
          @Override
          public final Long call() throws Exception {
            return provisionId();
          }
        };

    final TypePool typePool(
        final ClassFileLocator classFileLocator, final ClassLoader classLoader) {
      if (classLoader == null) {
        return createCachingTypePool(bootstrapCacheProvider, classFileLocator);
      }

      Long existingId = loaderIdCache.getIfPresent(classLoader);
      if (existingId != null) {
        return createCachingTypePool(existingId, classFileLocator);
      }

      if (exhaustedLoaderIdSeq()) {
        return createNonCachingTypePool(classFileLocator);
      }

      long provisionedId = 0;
      try {
        provisionedId = loaderIdCache.get(classLoader, this.provisionIdCallable);
      } catch (ExecutionException e) {
        log.error("unexpected exception", e);

        return createNonCachingTypePool(classFileLocator);
      }
      if (provisionedId == EXHAUSTED_ID) {
        return createNonCachingTypePool(classFileLocator);
      } else {
        return createCachingTypePool(provisionedId, classFileLocator);
      }
    }

    final boolean exhaustedLoaderIdSeq() {
      return (loaderIdSeq.get() >= LIMIT_ID);
    }

    final long provisionId() {
      do {
        long curId = loaderIdSeq.get();
        if (curId >= LIMIT_ID) return EXHAUSTED_ID;

        long newId = curId + 1;
        boolean acquired = loaderIdSeq.compareAndSet(curId, newId);
        if (acquired) return newId;
      } while (!Thread.currentThread().isInterrupted());

      return EXHAUSTED_ID;
    }

    private final TypePool createNonCachingTypePool(final ClassFileLocator classFileLocator) {
      return new TypePool.Default.WithLazyResolution(
          TypePool.CacheProvider.NoOp.INSTANCE, classFileLocator, TypePool.Default.ReaderMode.FAST);
    }

    private final TypePool.CacheProvider createCacheProvider(final long loaderId) {
      return new SharedResolutionCacheAdapter(loaderId, sharedResolutionCache);
    }

    private final TypePool createCachingTypePool(
        final long loaderId, final ClassFileLocator classFileLocator) {
      return new TypePool.Default.WithLazyResolution(
          createCacheProvider(loaderId),
          classFileLocator,
          TypePool.Default.ReaderMode.FAST);
    }

    private final TypePool createCachingTypePool(
        final TypePool.CacheProvider cacheProvider, final ClassFileLocator classFileLocator) {
      return new TypePool.Default.WithLazyResolution(
          cacheProvider, classFileLocator, TypePool.Default.ReaderMode.FAST);
    }

    final long approximateSize() {
      return sharedResolutionCache.size();
    }
  }

  /**
   * TypeCacheKey is key for the sharedResolutionCache. It is a mix of a cacheId/loaderId & a type
   * name.
   */
  static final class TypeCacheKey {
    private final long cacheId;
    private final String name;

    private final int hashCode;

    TypeCacheKey(final long cacheId, final String name) {
      this.cacheId = cacheId;
      this.name = name;

      hashCode = (int) (31 * cacheId) ^ name.hashCode();
    }

    @Override
    public final int hashCode() {
      return hashCode;
    }

    @Override
    public boolean equals(final Object obj) {
      if (!(obj instanceof TypeCacheKey)) return false;

      TypeCacheKey that = (TypeCacheKey) obj;
      return (cacheId == that.cacheId) && name.equals(that.name);
    }
  }

  static final class SharedResolutionCacheAdapter implements TypePool.CacheProvider {
    private static final String OBJECT_NAME = "java.lang.Object";
    private static final TypePool.Resolution OBJECT_RESOLUTION =
        new TypePool.Resolution.Simple(TypeDescription.OBJECT);

    private final long cacheId;
    private final Cache<TypeCacheKey, TypePool.Resolution> sharedResolutionCache;

    SharedResolutionCacheAdapter(
        final long cacheId, final Cache<TypeCacheKey, TypePool.Resolution> sharedResolutionCache) {
      this.cacheId = cacheId;
      this.sharedResolutionCache = sharedResolutionCache;
    }

    @Override
    public TypePool.Resolution find(final String name) {
      TypePool.Resolution existingResolution = sharedResolutionCache.getIfPresent(new TypeCacheKey(cacheId, name));
      if ( existingResolution != null ) return existingResolution;

      if ( OBJECT_NAME.equals(name) ) {
        return OBJECT_RESOLUTION;
      }

      return null;
    }

    @Override
    public TypePool.Resolution register(final String name, final TypePool.Resolution resolution) {
      if ( OBJECT_NAME.equals(name) ) {
        return resolution;
      }

      sharedResolutionCache.put(new TypeCacheKey(cacheId, name), resolution);
      return resolution;
    }

    @Override
    public void clear() {
      // Allowing the high-level eviction policy make the clearing decisions
    }
  }
}
