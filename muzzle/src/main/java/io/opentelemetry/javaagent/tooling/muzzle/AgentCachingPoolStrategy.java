/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.muzzle;

import io.opentelemetry.instrumentation.api.internal.cache.Cache;
import java.lang.ref.WeakReference;
import javax.annotation.Nullable;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.annotation.AnnotationList;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.MethodList;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.description.type.TypeList;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.pool.TypePool;

/**
 *
 *
 * <ul>
 *   There two core parts to the cache...
 *   <li>a cache of ClassLoader to WeakReference&lt;ClassLoader&gt;
 *   <li>a single cache of TypeResolutions for all ClassLoaders - keyed by a custom composite key of
 *       ClassLoader and class name
 * </ul>
 *
 * <p>This design was chosen to create a single limited size cache that can be adjusted for the
 * entire application -- without having to create a large number of WeakReference objects.
 *
 * <p>Eviction is handled through a size restriction
 */
public class AgentCachingPoolStrategy implements AgentBuilder.PoolStrategy {

  // Many things are package visible for testing purposes --
  // others to avoid creation of synthetic accessors

  static final int TYPE_CAPACITY = 64;

  static final int BOOTSTRAP_HASH = 7236344; // Just a random number

  /**
   * Cache of recent ClassLoader WeakReferences; used to...
   *
   * <ul>
   *   <li>Reduced number of WeakReferences created
   *   <li>Allow for quick fast path equivalence check of composite keys
   * </ul>
   */
  final Cache<ClassLoader, WeakReference<ClassLoader>> loaderRefCache = Cache.weak();

  /**
   * Single shared Type.Resolution cache -- uses a composite key -- conceptually of loader & name
   */
  final Cache<TypeCacheKey, TypePool.Resolution> sharedResolutionCache =
      Cache.bounded(TYPE_CAPACITY);

  // fast path for bootstrap
  final SharedResolutionCacheAdapter bootstrapCacheProvider =
      new SharedResolutionCacheAdapter(BOOTSTRAP_HASH, null, sharedResolutionCache);

  @Override
  public final TypePool typePool(ClassFileLocator classFileLocator, ClassLoader classLoader) {
    if (classLoader == null) {
      return createCachingTypePool(bootstrapCacheProvider, classFileLocator);
    }

    WeakReference<ClassLoader> loaderRef =
        loaderRefCache.computeIfAbsent(classLoader, WeakReference::new);

    int loaderHash = classLoader.hashCode();
    return createCachingTypePool(loaderHash, loaderRef, classFileLocator);
  }

  @Override
  public final TypePool typePool(
      ClassFileLocator classFileLocator, ClassLoader classLoader, String name) {
    return typePool(classFileLocator, classLoader);
  }

  private TypePool.CacheProvider createCacheProvider(
      int loaderHash, WeakReference<ClassLoader> loaderRef) {
    return new SharedResolutionCacheAdapter(loaderHash, loaderRef, sharedResolutionCache);
  }

  private TypePool createCachingTypePool(
      int loaderHash, WeakReference<ClassLoader> loaderRef, ClassFileLocator classFileLocator) {
    return new TypePool.Default.WithLazyResolution(
        createCacheProvider(loaderHash, loaderRef),
        classFileLocator,
        TypePool.Default.ReaderMode.FAST);
  }

  private static TypePool createCachingTypePool(
      TypePool.CacheProvider cacheProvider, ClassFileLocator classFileLocator) {
    return new TypePool.Default.WithLazyResolution(
        cacheProvider, classFileLocator, TypePool.Default.ReaderMode.FAST);
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

    TypeCacheKey(int loaderHash, WeakReference<ClassLoader> loaderRef, String className) {
      this.loaderHash = loaderHash;
      this.loaderRef = loaderRef;
      this.className = className;

      hashCode = 31 * loaderHash + className.hashCode();
    }

    @Override
    public boolean equals(@Nullable Object obj) {
      if (obj == this) {
        return true;
      }
      if (!(obj instanceof TypeCacheKey)) {
        return false;
      }

      TypeCacheKey other = (TypeCacheKey) obj;

      if (loaderHash != other.loaderHash) {
        return false;
      }

      if (!className.equals(other.className)) {
        return false;
      }

      // Fastpath loaderRef equivalence -- works because of WeakReference cache used
      // Also covers the bootstrap null loaderRef case
      if (loaderRef == other.loaderRef) {
        return true;
      }

      // need to perform a deeper loader check -- requires calling Reference.get
      // which can strengthen the Reference, so deliberately done last

      // If either reference has gone null, they aren't considered equivalent
      // Technically, this is a bit of violation of equals semantics, since
      // two equivalent references can become not equivalent.

      // In this case, it is fine because that means the ClassLoader is no
      // longer live, so the entries will never match anyway and will fall
      // out of the cache.
      ClassLoader thisLoader = loaderRef.get();
      if (thisLoader == null) {
        return false;
      }

      ClassLoader otherLoader = other.loaderRef.get();
      if (otherLoader == null) {
        return false;
      }

      return thisLoader == otherLoader;
    }

    @Override
    public final int hashCode() {
      return hashCode;
    }

    @Override
    public String toString() {
      return "TypeCacheKey{"
          + "loaderHash="
          + loaderHash
          + ", loaderRef="
          + loaderRef
          + ", className='"
          + className
          + '\''
          + '}';
    }
  }

  static final class SharedResolutionCacheAdapter implements TypePool.CacheProvider {
    private static final String OBJECT_NAME = "java.lang.Object";
    private static final TypePool.Resolution OBJECT_RESOLUTION =
        new TypePool.Resolution.Simple(new CachingTypeDescription(TypeDescription.OBJECT));

    private final int loaderHash;
    private final WeakReference<ClassLoader> loaderRef;
    private final Cache<TypeCacheKey, TypePool.Resolution> sharedResolutionCache;

    SharedResolutionCacheAdapter(
        int loaderHash,
        WeakReference<ClassLoader> loaderRef,
        Cache<TypeCacheKey, TypePool.Resolution> sharedResolutionCache) {
      this.loaderHash = loaderHash;
      this.loaderRef = loaderRef;
      this.sharedResolutionCache = sharedResolutionCache;
    }

    @Override
    public TypePool.Resolution find(String className) {
      TypePool.Resolution existingResolution =
          sharedResolutionCache.get(new TypeCacheKey(loaderHash, loaderRef, className));
      if (existingResolution != null) {
        return existingResolution;
      }

      if (OBJECT_NAME.equals(className)) {
        return OBJECT_RESOLUTION;
      }

      return null;
    }

    @Override
    public TypePool.Resolution register(String className, TypePool.Resolution resolution) {
      if (OBJECT_NAME.equals(className)) {
        return resolution;
      }

      resolution = new CachingResolution(resolution);

      sharedResolutionCache.put(new TypeCacheKey(loaderHash, loaderRef, className), resolution);
      return resolution;
    }

    @Override
    public void clear() {
      // Allowing the high-level eviction policy make the clearing decisions
    }
  }

  private static class CachingResolution implements TypePool.Resolution {
    private final TypePool.Resolution delegate;
    private TypeDescription cachedResolution;

    public CachingResolution(TypePool.Resolution delegate) {

      this.delegate = delegate;
    }

    @Override
    public boolean isResolved() {
      return delegate.isResolved();
    }

    @Override
    public TypeDescription resolve() {
      // Intentionally not "thread safe". Duplicate work deemed an acceptable trade-off.
      if (cachedResolution == null) {
        cachedResolution = new CachingTypeDescription(delegate.resolve());
      }
      return cachedResolution;
    }
  }

  /**
   * TypeDescription implementation that delegates and caches the results for the expensive calls
   * commonly used by our instrumentation.
   */
  private static class CachingTypeDescription
      extends TypeDescription.AbstractBase.OfSimpleType.WithDelegation {
    private final TypeDescription delegate;

    // These fields are intentionally not "thread safe".
    // Duplicate work deemed an acceptable trade-off.
    private Generic superClass;
    private TypeList.Generic interfaces;
    private AnnotationList annotations;
    private MethodList<MethodDescription.InDefinedShape> methods;

    public CachingTypeDescription(TypeDescription delegate) {
      this.delegate = delegate;
    }

    @Override
    protected TypeDescription delegate() {
      return delegate;
    }

    @Override
    public Generic getSuperClass() {
      if (superClass == null) {
        superClass = delegate.getSuperClass();
      }
      return superClass;
    }

    @Override
    public TypeList.Generic getInterfaces() {
      if (interfaces == null) {
        interfaces = delegate.getInterfaces();
      }
      return interfaces;
    }

    @Override
    public AnnotationList getDeclaredAnnotations() {
      if (annotations == null) {
        annotations = delegate.getDeclaredAnnotations();
      }
      return annotations;
    }

    @Override
    public MethodList<MethodDescription.InDefinedShape> getDeclaredMethods() {
      if (methods == null) {
        methods = delegate.getDeclaredMethods();
      }
      return methods;
    }

    @Override
    public String getName() {
      return delegate.getName();
    }
  }
}
