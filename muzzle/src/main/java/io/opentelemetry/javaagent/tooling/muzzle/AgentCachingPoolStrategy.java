/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.muzzle;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.instrumentation.api.internal.cache.Cache;
import io.opentelemetry.javaagent.bootstrap.InstrumentationHolder;
import io.opentelemetry.javaagent.bootstrap.VirtualFieldAccessorMarker;
import io.opentelemetry.javaagent.bootstrap.internal.AgentInstrumentationConfig;
import java.lang.instrument.Instrumentation;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.annotation.AnnotationList;
import net.bytebuddy.description.annotation.AnnotationValue;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.MethodList;
import net.bytebuddy.description.method.ParameterDescription;
import net.bytebuddy.description.method.ParameterList;
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
      AgentInstrumentationConfig.get()
          .getBoolean("otel.instrumentation.internal-reflection.enabled", true);
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
    // instrumentation is null when this code is called from muzzle
    Instrumentation instrumentation = InstrumentationHolder.getInstrumentation();
    if (instrumentation == null) {
      return null;
    }
    if (JavaModule.isSupported()) {
      // ensure that we have access to ClassLoader.findLoadedClass
      // if modular runtime is used export java.lang package from java.base module to the
      // module of this class
      JavaModule currentModule = JavaModule.ofType(AgentCachingPoolStrategy.class);
      JavaModule javaBase = JavaModule.ofType(ClassLoader.class);
      if (javaBase != null && javaBase.isNamed() && currentModule != null) {
        ClassInjector.UsingInstrumentation.redefineModule(
            instrumentation,
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

  private static boolean canUseFindLoadedClass() {
    return findLoadedClassMethod != null;
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

    int loaderHash = System.identityHashCode(classLoader);
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
    @Nullable private final WeakReference<ClassLoader> loaderRef;
    private final String className;

    private final int hashCode;

    TypeCacheKey(int loaderHash, WeakReference<ClassLoader> loaderRef, String className) {
      // classes in java package are always loaded from boot loader
      // set loader to boot loader to avoid creating multiple cache entries
      this.loaderHash = className.startsWith("java.") ? BOOTSTRAP_HASH : loaderHash;
      this.loaderRef = className.startsWith("java.") ? null : loaderRef;
      this.className = className;

      hashCode = 31 * this.loaderHash + className.hashCode();
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
      } else if (loaderRef == null || other.loaderRef == null) {
        return false;
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
        new TypePool.Resolution.Simple(TypeDescription.ForLoadedType.of(Object.class));

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
      if (OBJECT_NAME.equals(className)) {
        return OBJECT_RESOLUTION;
      }
      // Skip cache for the type that is currently being transformed.
      // If class has been transformed by another agent or by class loader it is possible that the
      // cached TypeDescription isn't the same as the one built from the actual bytes that are
      // being defined. For example if another agent adds an interface to the class then returning
      // the cached description that does not have that interface would result in bytebuddy removing
      // that interface.
      if (AgentTooling.isTransforming(loaderRef != null ? loaderRef.get() : null, className)) {
        return null;
      }

      TypePool.Resolution existingResolution =
          sharedResolutionCache.get(new TypeCacheKey(loaderHash, loaderRef, className));
      if (existingResolution != null) {
        return existingResolution;
      }

      return null;
    }

    @Override
    @CanIgnoreReturnValue
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
    // ThreadLocal used for detecting loading of annotation types
    private final ThreadLocal<Boolean> loadingAnnotations = new ThreadLocal<>();
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
    @CanIgnoreReturnValue
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
        // calling super.doDescribe that will locate the class bytes and parse them unlike
        // doDescribe in this class that returns a lazy resolution without parsing the class bytes
        resolution = cacheProvider.register(name, super.doDescribe(name));
      }
      return resolution;
    }

    void enterLoadAnnotations() {
      loadingAnnotations.set(Boolean.TRUE);
    }

    void exitLoadAnnotations() {
      loadingAnnotations.set(null);
    }

    boolean isLoadingAnnotations() {
      return loadingAnnotations.get() != null;
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
        // Like jdk, byte-buddy getDeclaredAnnotations does not report annotations whose class is
        // missing. To do this it needs to locate the bytes for annotation types used in class.
        // Which means that if we have a matcher that matches methods annotated with @Foo byte-buddy
        // will end up locating bytes for all annotations used on any method in the classes that
        // this matcher is applied to. From our perspective this is unreasonable, we just want to
        // match based on annotation name with as little overhead as possible. As we match only
        // based on annotation name we never need to locate the bytes for the annotation type.
        // See TypePool.Default.LazyTypeDescription.LazyAnnotationDescription.asList()
        // When isResolved() is called during loading of annotations delay resolving to avoid
        // looking up the class bytes.
        if (isLoadingAnnotations()) {
          return true;
        }
        return doResolve(name).isResolved();
      }

      private volatile TypeDescription cached;

      @Override
      public TypeDescription resolve() {
        // unlike byte-buddy implementation we cache the descriptor to avoid having to find
        // super class and interfaces multiple times
        if (cached == null) {
          cached = new AgentTypePool.LazyTypeDescription(classLoaderRef, name);
          // if we know that an annotation is being loaded wrap the result so that we wouldn't
          // need to resolve the class bytes to tell whether it is an annotation
          if (isLoadingAnnotations()) {
            cached = new AnnotationTypeDescription(cached);
          }
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
          TypeDescription delegate = delegate();
          // Run getDeclaredAnnotations with ThreadLocal. ThreadLocal helps us detect types looked
          // up by getDeclaredAnnotations and treat them specially.
          enterLoadAnnotations();
          try {
            annotations = delegate.getDeclaredAnnotations();
          } finally {
            exitLoadAnnotations();
          }
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
     * Based on TypePool.Default.WithLazyResolution.LazyTypeDescription
     *
     * <p>Class description that attempts to use already loaded super classes for navigating class
     * hierarchy.
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

      private volatile TypeDescription.Generic cachedSuperClass;

      @Override
      public TypeDescription.Generic getSuperClass() {
        if (cachedSuperClass == null) {
          TypeDescription.Generic superClassDescription = delegate().getSuperClass();
          ClassLoader classLoader = classLoaderRef.get();
          if (canUseFindLoadedClass() && classLoader != null && superClassDescription != null) {
            String superName = superClassDescription.getTypeName();
            Class<?> superClass = findLoadedClass(classLoader, superName);
            if (superClass != null) {
              // here we use raw type and loose generic info
              // we don't expect to have matchers that would use the generic info
              superClassDescription = newTypeDescription(superClass).asGenericType();
            }
          }
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
          if (canUseFindLoadedClass() && classLoader != null && !interfaces.isEmpty()) {
            // here we use raw types and loose generic info
            // we don't expect to have matchers that would use the generic info
            List<TypeDescription> result = new ArrayList<>();
            for (TypeDescription.Generic interfaceDescription : interfaces) {
              String interfaceName = interfaceDescription.getTypeName();
              Class<?> interfaceClass = findLoadedClass(classLoader, interfaceName);
              if (interfaceClass != null) {
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

      private class LazyAnnotationMethodDescription extends DelegatingMethodDescription {

        LazyAnnotationMethodDescription(MethodDescription.InDefinedShape method) {
          super(method);
        }

        @Override
        public AnnotationList getDeclaredAnnotations() {
          // Run getDeclaredAnnotations with ThreadLocal. ThreadLocal helps us detect types looked
          // up by getDeclaredAnnotations and treat them specially.
          enterLoadAnnotations();
          try {
            return method.getDeclaredAnnotations();
          } finally {
            exitLoadAnnotations();
          }
        }
      }

      @Override
      public MethodList<MethodDescription.InDefinedShape> getDeclaredMethods() {
        MethodList<MethodDescription.InDefinedShape> methods = super.getDeclaredMethods();

        class MethodListWrapper extends MethodList.AbstractBase<MethodDescription.InDefinedShape> {

          @Override
          public MethodDescription.InDefinedShape get(int index) {
            return new LazyAnnotationMethodDescription(methods.get(index));
          }

          @Override
          public int size() {
            return methods.size();
          }
        }

        return new MethodListWrapper();
      }
    }

    private AgentTypePool.LazyTypeDescriptionWithClass newTypeDescription(Class<?> clazz) {
      return newLazyTypeDescriptionWithClass(
          AgentTypePool.this, AgentCachingPoolStrategy.this, clazz);
    }

    /**
     * Based on TypePool.Default.WithLazyResolution.LazyTypeDescription
     *
     * <p>Class description that uses an existing class instance for navigating super class
     * hierarchy. This should be much more efficient than finding super types through resource
     * lookups and parsing bytecode. We are not using TypeDescription.ForLoadedType as it can cause
     * additional classes to be loaded.
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

      private volatile TypeDescription.Generic cachedSuperClass;

      @Override
      public TypeDescription.Generic getSuperClass() {
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

  /**
   * Class descriptor that claims to represent an annotation without checking whether the underlying
   * type really is an annotation.
   */
  private static class AnnotationTypeDescription
      extends TypeDescription.AbstractBase.OfSimpleType.WithDelegation {
    private final TypeDescription delegate;

    AnnotationTypeDescription(TypeDescription delegate) {
      this.delegate = delegate;
    }

    @Override
    protected TypeDescription delegate() {
      return delegate;
    }

    @Override
    public String getName() {
      return delegate.getName();
    }

    @Override
    public boolean isAnnotation() {
      // by default byte-buddy checks whether class modifiers have annotation bit set
      // as we wish to avoid looking up the class bytes we assume that every class that was expected
      // to be an annotation really is an annotation and return true here
      // See TypePool.Default.LazyTypeDescription.LazyAnnotationDescription.asList()
      return true;
    }
  }

  private static class DelegatingMethodDescription
      extends MethodDescription.InDefinedShape.AbstractBase {
    protected final MethodDescription.InDefinedShape method;

    DelegatingMethodDescription(MethodDescription.InDefinedShape method) {
      this.method = method;
    }

    @Nonnull
    @Override
    public TypeDescription getDeclaringType() {
      return method.getDeclaringType();
    }

    @Override
    public TypeDescription.Generic getReturnType() {
      return method.getReturnType();
    }

    @Override
    public ParameterList<ParameterDescription.InDefinedShape> getParameters() {
      return method.getParameters();
    }

    @Override
    public TypeList.Generic getExceptionTypes() {
      return method.getExceptionTypes();
    }

    @Override
    public AnnotationValue<?, ?> getDefaultValue() {
      return method.getDefaultValue();
    }

    @Override
    public String getInternalName() {
      return method.getInternalName();
    }

    @Override
    public TypeList.Generic getTypeVariables() {
      return method.getTypeVariables();
    }

    @Override
    public int getModifiers() {
      return method.getModifiers();
    }

    @Override
    public AnnotationList getDeclaredAnnotations() {
      return method.getDeclaredAnnotations();
    }
  }
}
