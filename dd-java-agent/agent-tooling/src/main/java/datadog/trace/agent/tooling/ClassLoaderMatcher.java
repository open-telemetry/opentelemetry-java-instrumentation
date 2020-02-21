package datadog.trace.agent.tooling;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import datadog.trace.bootstrap.PatchLogger;
import io.opentracing.util.GlobalTracer;
import java.util.concurrent.ExecutionException;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.matcher.ElementMatcher;

@Slf4j
public class ClassLoaderMatcher {
  public static final ClassLoader BOOTSTRAP_CLASSLOADER = null;

  /** A private constructor that must not be invoked. */
  private ClassLoaderMatcher() {
    throw new UnsupportedOperationException();
  }

  public static ElementMatcher.Junction.AbstractBase<ClassLoader> skipClassLoader() {
    return SkipClassLoaderMatcher.INSTANCE;
  }

  public static ElementMatcher.Junction.AbstractBase<ClassLoader> classLoaderHasClasses(
      final String... names) {
    return new ClassLoaderHasClassMatcher(names);
  }

  private static final class SkipClassLoaderMatcher
      extends ElementMatcher.Junction.AbstractBase<ClassLoader> {
    public static final SkipClassLoaderMatcher INSTANCE = new SkipClassLoaderMatcher();
    /* Cache of classloader-instance -> (true|false). True = skip instrumentation. False = safe to instrument. */
    private static final LoadingCache<ClassLoader, Boolean> skipCache =
        CacheBuilder.newBuilder()
            .weakKeys()
            .build(
                new CacheLoader<ClassLoader, Boolean>() {
                  @Override
                  public Boolean load(ClassLoader loader) {
                    return !delegatesToBootstrap(loader);
                  }
                });
    private static final String DATADOG_CLASSLOADER_NAME =
        "datadog.trace.bootstrap.DatadogClassLoader";

    private SkipClassLoaderMatcher() {}

    @Override
    public boolean matches(final ClassLoader target) {
      if (target == BOOTSTRAP_CLASSLOADER) {
        // Don't skip bootstrap loader
        return false;
      }
      return shouldSkipClass(target) || shouldSkipInstance(target);
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

    private static boolean shouldSkipInstance(final ClassLoader loader) {
      try {
        return skipCache.get(loader);
      } catch (ExecutionException e) {
        log.warn("Exception while getting from cache", e);
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

  public static class ClassLoaderHasClassMatcher
      extends ElementMatcher.Junction.AbstractBase<ClassLoader> {

    private final LoadingCache<ClassLoader, Boolean> cache =
        CacheBuilder.newBuilder()
            .weakKeys()
            .build(
                new CacheLoader<ClassLoader, Boolean>() {
                  @Override
                  public Boolean load(ClassLoader cl) {
                    for (final String name : names) {
                      if (cl.getResource(Utils.getResourceName(name)) == null) {
                        return false;
                      }
                    }
                    return true;
                  }
                });

    private final String[] names;

    private ClassLoaderHasClassMatcher(final String... names) {
      this.names = names;
    }

    @Override
    public boolean matches(final ClassLoader target) {
      if (target != null) {
        try {
          return cache.get(target);
        } catch (ExecutionException e) {
          log.warn("Can't get from cache", e);
        }
      }
      return false;
    }
  }
}
