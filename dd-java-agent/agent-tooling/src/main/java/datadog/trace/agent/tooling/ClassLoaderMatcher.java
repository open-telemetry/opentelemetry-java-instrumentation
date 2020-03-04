package datadog.trace.agent.tooling;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
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
    private static final String DATADOG_CLASSLOADER_NAME =
        "datadog.trace.bootstrap.DatadogClassLoader";

    private SkipClassLoaderMatcher() {}

    @Override
    public boolean matches(final ClassLoader cl) {
      if (cl == BOOTSTRAP_CLASSLOADER) {
        // Don't skip bootstrap loader
        return false;
      }
      return shouldSkipClass(cl);
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
  }

  private static class ClassLoaderHasNoResourceMatcher
      extends ElementMatcher.Junction.AbstractBase<ClassLoader> {
    private final Cache<ClassLoader, Boolean> cache =
        CacheBuilder.newBuilder().weakKeys().concurrencyLevel(CACHE_CONCURRENCY).build();

    private final String[] resources;

    private ClassLoaderHasNoResourceMatcher(final String... resources) {
      this.resources = resources;
    }

    private boolean hasNoResources(final ClassLoader cl) {
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
