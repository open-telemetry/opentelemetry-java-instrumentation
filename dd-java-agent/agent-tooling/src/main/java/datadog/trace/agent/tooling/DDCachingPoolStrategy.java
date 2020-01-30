package datadog.trace.agent.tooling;

import static net.bytebuddy.agent.builder.AgentBuilder.PoolStrategy;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.lang.ref.WeakReference;
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
 *   <li>a cache of ClassLoader to WeakReference&lt;ClassLoader&gt;
 *   <li>a single cache of TypeResolutions for all ClassLoaders - keyed by a custom composite key of
 *       ClassLoader & class name
 * </ul>
 *
 * <p>This design was chosen to create a single limited size cache that can be adjusted for the
 * entire application -- without having to create a large number of WeakReference objects.
 *
 * <p>Eviction is handled almost entirely through a size restriction; however, softValues are still
 * used as a further safeguard.
 */
@Slf4j
public class DDCachingPoolStrategy implements PoolStrategy {
  // Many things are package visible for testing purposes --
  // others to avoid creation of synthetic accessors

  static final int CONCURRENCY_LEVEL = 8;
  static final int LOADER_CAPACITY = 64;
  static final int TYPE_CAPACITY = 64;

  static final int BOOTSTRAP_HASH = 0;

  /**
   * Cache of recent ClassLoader WeakReferences; used to...
   *
   * <ul>
   *   <li>Reduced number of WeakReferences created
   *   <li>Allow for quick fast path equivalence check of composite keys
   * </ul>
   */
  final Cache<ClassLoader, WeakReference<ClassLoader>> loaderRefCache =
      CacheBuilder.newBuilder()
          .weakKeys()
          .concurrencyLevel(CONCURRENCY_LEVEL)
          .initialCapacity(LOADER_CAPACITY / 2)
          .maximumSize(LOADER_CAPACITY)
          .build();

  /**
   * Single shared Type.Resolution cache -- uses a composite key -- conceptually of loader & name
   */
  final Cache<TypeCacheKey, TypePool.Resolution> sharedResolutionCache =
      CacheBuilder.newBuilder()
          .softValues()
          .concurrencyLevel(CONCURRENCY_LEVEL)
          .initialCapacity(TYPE_CAPACITY)
          .maximumSize(TYPE_CAPACITY)
          .build();

  /** Fast path for bootstrap */
  final SharedResolutionCacheAdapter bootstrapCacheProvider =
      new SharedResolutionCacheAdapter(BOOTSTRAP_HASH, null, sharedResolutionCache);

  @Override
  public final TypePool typePool(
      final ClassFileLocator classFileLocator, final ClassLoader classLoader) {
    if (classLoader == null) {
      return createCachingTypePool(bootstrapCacheProvider, classFileLocator);
    }

    WeakReference<ClassLoader> loaderRef = loaderRefCache.getIfPresent(classLoader);

    if (loaderRef == null) {
      loaderRef = new WeakReference<>(classLoader);
      loaderRefCache.put(classLoader, loaderRef);
    }

    int loaderHash = classLoader.hashCode();
    return createCachingTypePool(loaderHash, loaderRef, classFileLocator);
  }

  private final TypePool.CacheProvider createCacheProvider(
      final int loaderHash, final WeakReference<ClassLoader> loaderRef) {
    return new SharedResolutionCacheAdapter(loaderHash, loaderRef, sharedResolutionCache);
  }

  private final TypePool createCachingTypePool(
      final int loaderHash,
      final WeakReference<ClassLoader> loaderRef,
      final ClassFileLocator classFileLocator) {
    return new TypePool.Default.WithLazyResolution(
        createCacheProvider(loaderHash, loaderRef),
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

  /**
   * TypeCacheKey is key for the sharedResolutionCache. Conceptually, it is a mix of ClassLoader &
   * class name.
   *
   * <p>For efficiency & GC purposes, it is actually composed of loaderHash &
   * WeakReference&lt;ClassLoader&gt;
   *
   * <p>The loaderHash exists to avoid calling get & strengthening the Reference.
   */
  static final class TypeCacheKey {
    private final int loaderHash;
    private final WeakReference<ClassLoader> loaderRef;
    private final String className;

    private final int hashCode;

    TypeCacheKey(
        final int loaderHash, final WeakReference<ClassLoader> loaderRef, final String className) {
      this.loaderHash = loaderHash;
      this.loaderRef = loaderRef;
      this.className = className;

      hashCode = (int) (31 * this.loaderHash) ^ className.hashCode();
    }

    @Override
    public final int hashCode() {
      return hashCode;
    }

    @Override
    public boolean equals(final Object obj) {
      if (!(obj instanceof TypeCacheKey)) return false;

      TypeCacheKey that = (TypeCacheKey) obj;

      if (loaderHash != that.loaderHash) return false;

      // Fastpath loaderRef equivalence -- works because of WeakReference cache used
      // Also covers the bootstrap null loaderRef case
      if (loaderRef == that.loaderRef) {
        // still need to check name
        return className.equals(that.className);
      } else if (className.equals(that.className)) {
        // need to perform a deeper loader check -- requires calling Reference.get
        // which can strengthen the Reference, so deliberately done last

        // If either reference has gone null, they aren't considered equivalent
        // Technically, this is a bit of violation of equals semantics, since
        // two equivalent references can become not equivalent.

        // In this case, it is fine because that means the ClassLoader is no
        // longer live, so the entries will never match anyway and will fall
        // out of the cache.
        ClassLoader thisLoader = loaderRef.get();
        if (thisLoader == null) return false;

        ClassLoader thatLoader = that.loaderRef.get();
        if (thatLoader == null) return false;

        return (thisLoader == thatLoader);
      } else {
        return false;
      }
    }
  }

  static final class SharedResolutionCacheAdapter implements TypePool.CacheProvider {
    private static final String OBJECT_NAME = "java.lang.Object";
    private static final TypePool.Resolution OBJECT_RESOLUTION =
        new TypePool.Resolution.Simple(TypeDescription.OBJECT);

    private final int loaderHash;
    private final WeakReference<ClassLoader> loaderRef;
    private final Cache<TypeCacheKey, TypePool.Resolution> sharedResolutionCache;

    SharedResolutionCacheAdapter(
        final int loaderHash,
        final WeakReference<ClassLoader> loaderRef,
        final Cache<TypeCacheKey, TypePool.Resolution> sharedResolutionCache) {
      this.loaderHash = loaderHash;
      this.loaderRef = loaderRef;
      this.sharedResolutionCache = sharedResolutionCache;
    }

    @Override
    public TypePool.Resolution find(final String className) {
      TypePool.Resolution existingResolution =
          sharedResolutionCache.getIfPresent(new TypeCacheKey(loaderHash, loaderRef, className));
      if (existingResolution != null) return existingResolution;

      if (OBJECT_NAME.equals(className)) {
        return OBJECT_RESOLUTION;
      }

      return null;
    }

    @Override
    public TypePool.Resolution register(
        final String className, final TypePool.Resolution resolution) {
      if (OBJECT_NAME.equals(className)) {
        return resolution;
      }

      sharedResolutionCache.put(new TypeCacheKey(loaderHash, loaderRef, className), resolution);
      return resolution;
    }

    @Override
    public void clear() {
      // Allowing the high-level eviction policy make the clearing decisions
    }
  }
}
