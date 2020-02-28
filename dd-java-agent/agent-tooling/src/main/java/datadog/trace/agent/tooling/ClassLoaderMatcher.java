package datadog.trace.agent.tooling;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import datadog.trace.bootstrap.PatchLogger;
import io.opentracing.util.GlobalTracer;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.matcher.ElementMatcher;

@Slf4j
public final class ClassLoaderMatcher {
  public static final ClassLoader BOOTSTRAP_CLASSLOADER = null;
  public static final int CACHE_CONCURRENCY =
      Math.max(8, Runtime.getRuntime().availableProcessors());

  /** A private constructor that must not be invoked. */
  private ClassLoaderMatcher() {
    throw new UnsupportedOperationException();
  }

  public static ElementMatcher.Junction.AbstractBase<ClassLoader> skipClassLoader() {
    return SkipClassLoaderMatcher.INSTANCE;
  }

  public static ElementMatcher.Junction.AbstractBase<ClassLoader> classLoaderHasNoResources(
      final String... resources) {
    return new ClassLoaderHasNoResourceMatcher(resources);
  }

  private static final class SkipClassLoaderMatcher
      extends ElementMatcher.Junction.AbstractBase<ClassLoader> {
    public static final SkipClassLoaderMatcher INSTANCE = new SkipClassLoaderMatcher();
    /* Cache of classloader-instance -> (true|false). True = skip instrumentation. False = safe to instrument. */
    private static final Cache<ClassLoader, Boolean> skipCache =
        CacheBuilder.newBuilder().weakKeys().concurrencyLevel(CACHE_CONCURRENCY).build();
    private static final String DATADOG_CLASSLOADER_NAME =
        "datadog.trace.bootstrap.DatadogClassLoader";

    private SkipClassLoaderMatcher() {}

    @Override
    public boolean matches(final ClassLoader cl) {
      if (cl == BOOTSTRAP_CLASSLOADER) {
        // Don't skip bootstrap loader
        return false;
      }
      Boolean v = skipCache.getIfPresent(cl);
      if (v != null) {
        return v;
      }
      v = shouldSkipClass(cl) || !delegatesToBootstrap(cl);
      skipCache.put(cl, v);
      return v;
    }

    private static boolean shouldSkipClass(final ClassLoader loader) {
      switch (loader.getClass().getName()) {
        case "org.codehaus.groovy.runtime.callsite.CallSiteClassLoader":
        case "sun.reflect.DelegatingClassLoader":
        case "jdk.internal.reflect.DelegatingClassLoader":
        case "clojure.lang.DynamicClassLoader":
        case "org.apache.cxf.common.util.ASMHelper$TypeHelperClassLoader":
        case "sun.misc.Launcher$ExtClassLoader":
        case DATADOG_CLASSLOADER_NAME:
          return true;
      }
      return false;
    }

    /**
     * TODO: this turns out to be useless with OSGi: {@code
     * org.eclipse.osgi.internal.loader.BundleLoader#isRequestFromVM} returns {@code true} when
     * class loading is issued from this check and {@code false} for 'real' class loads. We should
     * come up with some sort of hack to avoid this problem.
     */
    private static boolean delegatesToBootstrap(final ClassLoader loader) {
      boolean delegates = true;
      if (!loadsExpectedClass(loader, GlobalTracer.class)) {
        log.debug("loader {} failed to delegate bootstrap opentracing class", loader);
        delegates = false;
      }
      if (!loadsExpectedClass(loader, PatchLogger.class)) {
        log.debug("loader {} failed to delegate bootstrap datadog class", loader);
        delegates = false;
      }
      return delegates;
    }

    private static boolean loadsExpectedClass(
        final ClassLoader loader, final Class<?> expectedClass) {
      try {
        return loader.loadClass(expectedClass.getName()) == expectedClass;
      } catch (final ClassNotFoundException e) {
        return false;
      }
    }
  }

  private static class ClassLoaderHasNoResourceMatcher
      extends ElementMatcher.Junction.AbstractBase<ClassLoader> {
    private final Cache<ClassLoader, Boolean> cache =
        CacheBuilder.newBuilder().weakKeys().concurrencyLevel(CACHE_CONCURRENCY).build();

    private final String[] resources;

    private ClassLoaderHasNoResourceMatcher(final String... resources) {
      this.resources = resources;
    }

    private boolean hasNoResources(ClassLoader cl) {
      for (final String resource : resources) {
        if (cl.getResource(resource) == null) {
          return true;
        }
      }
      return false;
    }

    @Override
    public boolean matches(final ClassLoader cl) {
      if (cl == null) {
        return false;
      }
      Boolean v = cache.getIfPresent(cl);
      if (v != null) {
        return v;
      }
      v = hasNoResources(cl);
      cache.put(cl, v);
      return v;
    }
  }
}
