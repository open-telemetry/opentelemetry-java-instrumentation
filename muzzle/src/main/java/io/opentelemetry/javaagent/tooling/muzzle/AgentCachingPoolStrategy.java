/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.muzzle;

import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.instrumentation.api.internal.cache.Cache;
import io.opentelemetry.javaagent.bootstrap.InstrumentationHolder;
import io.opentelemetry.javaagent.bootstrap.VirtualFieldAccessorMarker;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.annotation.AnnotationList;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.MethodList;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.description.type.TypeList;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.loading.ClassInjector;
import net.bytebuddy.pool.TypePool;
import net.bytebuddy.utility.JavaModule;

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

  private static final boolean REFLECTION_ENABLED =
      Config.get().isInstrumentationEnabled(Collections.singleton("internal-reflection"), true);
  private static final Method findLoadedClassMethod = getFindLoadedClassMethod();

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

  private final AgentLocationStrategy locationStrategy;

  public AgentCachingPoolStrategy(AgentLocationStrategy locationStrategy) {
    this.locationStrategy = locationStrategy;
  }

  private static Method getFindLoadedClassMethod() {
    if (JavaModule.isSupported()) {
      JavaModule currentModule = JavaModule.ofType(AgentCachingPoolStrategy.class);
      JavaModule javaBase = JavaModule.ofType(ClassLoader.class);
      if (javaBase != null && javaBase.isNamed() && currentModule != null) {
        ClassInjector.UsingInstrumentation.redefineModule(
            InstrumentationHolder.getInstrumentation(),
            javaBase,
            Collections.emptySet(),
            Collections.emptyMap(),
            Collections.singletonMap("java.lang", Collections.singleton(currentModule)),
            Collections.emptySet(),
            Collections.emptyMap());
      }
    }
    try {
      Method method = ClassLoader.class.getDeclaredMethod("findLoadedClass", String.class);
      method.setAccessible(true);
      return method;
    } catch (NoSuchMethodException exception) {
      throw new IllegalStateException(exception);
    }
  }

  private static Class<?> findLoadedClass(ClassLoader classLoader, String className) {
    try {
      return (Class<?>) findLoadedClassMethod.invoke(classLoader, className);
    } catch (Exception exception) {
      throw new IllegalStateException(exception);
    }
  }

  @Override
  public AgentTypePool typePool(ClassFileLocator classFileLocator, ClassLoader classLoader) {
    return new AgentTypePool(
        getCacheProvider(classLoader),
        classFileLocator,
        classLoader,
        TypePool.Default.ReaderMode.FAST);
  }

  @Override
  public AgentTypePool typePool(
      ClassFileLocator classFileLocator, ClassLoader classLoader, String name) {
    return typePool(classFileLocator, classLoader);
  }

  private TypePool.CacheProvider getCacheProvider(ClassLoader classLoader) {
    if (classLoader == null) {
      return bootstrapCacheProvider;
    }

    WeakReference<ClassLoader> loaderRef =
        loaderRefCache.computeIfAbsent(classLoader, WeakReference::new);

    int loaderHash = classLoader.hashCode();
    return new SharedResolutionCacheAdapter(loaderHash, loaderRef, sharedResolutionCache);
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
  private static final class TypeCacheKey {
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
    public int hashCode() {
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

  private static final class SharedResolutionCacheAdapter implements TypePool.CacheProvider {
    private static final String OBJECT_NAME = "java.lang.Object";
    private static final TypePool.Resolution OBJECT_RESOLUTION =
        new TypePool.Resolution.Simple(TypeDescription.OBJECT);

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

      sharedResolutionCache.put(new TypeCacheKey(loaderHash, loaderRef, className), resolution);
      return resolution;
    }

    @Override
    public void clear() {
      // Allowing the high-level eviction policy make the clearing decisions
    }
  }

  /** Based on TypePool.Default.WithLazyResolution */
  private class AgentTypePool extends TypePool.Default {
    private final WeakReference<ClassLoader> classLoaderRef;

    public AgentTypePool(
        TypePool.CacheProvider cacheProvider,
        ClassFileLocator classFileLocator,
        ClassLoader classLoader,
        TypePool.Default.ReaderMode readerMode) {
      super(cacheProvider, classFileLocator, readerMode);
      this.classLoaderRef = new WeakReference<>(classLoader);
    }

    @Override
    protected TypePool.Resolution doDescribe(String name) {
      return new AgentTypePool.LazyResolution(classLoaderRef, name);
    }

    @Override
    protected TypePool.Resolution doCache(String name, TypePool.Resolution resolution) {
      return resolution;
    }

    /**
     * Non-lazily resolves a type name.
     *
     * @param name The name of the type to resolve.
     * @return The resolution for the type of this name.
     */
    protected TypePool.Resolution doResolve(String name) {
      TypePool.Resolution resolution = cacheProvider.find(name);
      if (resolution == null) {
        resolution = cacheProvider.register(name, super.doDescribe(name));
      }
      return resolution;
    }

    /** Based on TypePool.Default.WithLazyResolution.LazyResolution */
    private class LazyResolution implements TypePool.Resolution {
      private final WeakReference<ClassLoader> classLoaderRef;
      private final String name;

      LazyResolution(WeakReference<ClassLoader> classLoaderRef, String name) {
        this.classLoaderRef = classLoaderRef;
        this.name = name;
      }

      @Override
      public boolean isResolved() {
        return doResolve(name).isResolved();
      }

      private volatile TypeDescription cached;

      @Override
      public TypeDescription resolve() {
        // unlike byte-buddy implementation we cache the descriptor to avoid having to find
        // super class and interfaces multiple times
        if (cached == null) {
          cached = new AgentTypePool.LazyTypeDescription(classLoaderRef, name);
        }
        return cached;
      }
    }

    private abstract class CachingTypeDescription
        extends TypeDescription.AbstractBase.OfSimpleType.WithDelegation {
      private volatile TypeDescription delegate;

      @Override
      protected TypeDescription delegate() {
        if (delegate == null) {
          delegate = doResolve(getName()).resolve();
        }
        return delegate;
      }

      private volatile AnnotationList annotations;

      @Override
      public AnnotationList getDeclaredAnnotations() {
        if (annotations == null) {
          annotations = delegate().getDeclaredAnnotations();
        }
        return annotations;
      }

      private volatile MethodList<MethodDescription.InDefinedShape> methods;

      @Override
      public MethodList<MethodDescription.InDefinedShape> getDeclaredMethods() {
        if (methods == null) {
          methods = delegate().getDeclaredMethods();
        }
        return methods;
      }
    }

    /**
     * Based on TypePool.Default.WithLazyResolution.LazyTypeDescription Class description that
     * attempts to use already loaded super classes for navigating class hierarchy.
     */
    private class LazyTypeDescription extends AgentTypePool.CachingTypeDescription {
      // using WeakReference to ensure that caching this descriptor won't keep class loader alive
      private final WeakReference<ClassLoader> classLoaderRef;
      private final String name;

      LazyTypeDescription(WeakReference<ClassLoader> classLoaderRef, String name) {
        this.classLoaderRef = classLoaderRef;
        this.name = name;
      }

      @Override
      public String getName() {
        return name;
      }

      private volatile Generic cachedSuperClass;

      @Override
      public Generic getSuperClass() {
        if (cachedSuperClass == null) {
          Generic superClassDescription = delegate().getSuperClass();
          ClassLoader classLoader = classLoaderRef.get();
          if (classLoader != null && superClassDescription != null) {
            String superName = superClassDescription.getTypeName();
            Class<?> superClass = findLoadedClass(classLoader, superName);
            if (superClass != null) {
              superClassDescription = newTypeDescription(superClass).asGenericType();
            }
          }
          // using raw type
          cachedSuperClass = superClassDescription;
        }
        return cachedSuperClass;
      }

      private volatile TypeList.Generic cachedInterfaces;

      @Override
      public TypeList.Generic getInterfaces() {
        if (cachedInterfaces == null) {
          TypeList.Generic interfaces = delegate().getInterfaces();
          ClassLoader classLoader = classLoaderRef.get();
          if (classLoader != null && !interfaces.isEmpty()) {
            List<TypeDescription> result = new ArrayList<>();
            for (Generic interfaceDescription : interfaces) {
              String interfaceName = interfaceDescription.getTypeName();
              Class<?> interfaceClass = findLoadedClass(classLoader, interfaceName);
              if (interfaceClass != null) {
                // using raw type
                result.add(newTypeDescription(interfaceClass));
              } else {
                result.add(interfaceDescription.asErasure());
              }
            }
            interfaces = new TypeList.Generic.Explicit(result);
          }

          cachedInterfaces = interfaces;
        }
        return cachedInterfaces;
      }
    }

    private AgentTypePool.LazyTypeDescriptionWithClass newTypeDescription(Class<?> clazz) {
      return newLazyTypeDescriptionWithClass(
          AgentTypePool.this, AgentCachingPoolStrategy.this, clazz);
    }

    /**
     * Based on TypePool.Default.WithLazyResolution.LazyTypeDescription Class description that uses
     * an existing class instance for navigating super class hierarchy This should be much more
     * efficient than finding super types through resource lookups and parsing bytecode. We are not
     * using TypeDescription.ForLoadedType as it can cause additional classes to be loaded.
     */
    private class LazyTypeDescriptionWithClass extends AgentTypePool.CachingTypeDescription {
      // using WeakReference to ensure that caching this descriptor won't keep class loader alive
      private final WeakReference<Class<?>> classRef;
      private final String name;
      private final int modifiers;

      LazyTypeDescriptionWithClass(Class<?> clazz) {
        this.name = clazz.getName();
        this.modifiers = clazz.getModifiers();
        this.classRef = new WeakReference<>(clazz);
      }

      @Override
      public String getName() {
        return name;
      }

      private volatile Generic cachedSuperClass;

      @Override
      public Generic getSuperClass() {
        if (cachedSuperClass == null) {
          Class<?> clazz = classRef.get();
          if (clazz == null) {
            return null;
          }
          Class<?> superClass = clazz.getSuperclass();
          if (superClass == null) {
            return null;
          }
          // using raw type
          cachedSuperClass = newTypeDescription(superClass).asGenericType();
        }

        return cachedSuperClass;
      }

      private volatile TypeList.Generic cachedInterfaces;

      @Override
      public TypeList.Generic getInterfaces() {
        if (cachedInterfaces == null) {
          List<TypeDescription> result = new ArrayList<>();
          Class<?> clazz = classRef.get();
          if (clazz != null) {
            for (Class<?> interfaceClass : clazz.getInterfaces()) {
              // virtual field accessors are removed by internal-reflection instrumentation
              // we do this extra check for tests run with internal-reflection disabled
              if (!REFLECTION_ENABLED
                  && VirtualFieldAccessorMarker.class.isAssignableFrom(interfaceClass)) {
                continue;
              }
              // using raw type
              result.add(newTypeDescription(interfaceClass));
            }
          }
          cachedInterfaces = new TypeList.Generic.Explicit(result);
        }

        return cachedInterfaces;
      }

      @Override
      public int getModifiers() {
        return modifiers;
      }
    }
  }

  private static AgentTypePool.LazyTypeDescriptionWithClass newLazyTypeDescriptionWithClass(
      AgentTypePool pool, AgentCachingPoolStrategy poolStrategy, Class<?> clazz) {
    // if class and existing pool use different class loaders create a new pool with correct class
    // loader
    if (pool.classLoaderRef.get() != clazz.getClassLoader()) {
      ClassFileLocator classFileLocator =
          poolStrategy.locationStrategy.classFileLocator(clazz.getClassLoader());
      pool = poolStrategy.typePool(classFileLocator, clazz.getClassLoader());
    }
    return pool.new LazyTypeDescriptionWithClass(clazz);
  }
}
