package datadog.trace.agent.tooling;

import static datadog.trace.bootstrap.WeakMap.Provider.newWeakMap;

import datadog.trace.bootstrap.DatadogClassLoader;
import datadog.trace.bootstrap.PatchLogger;
import datadog.trace.bootstrap.WeakMap;
import io.opentracing.util.GlobalTracer;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
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

  public static ElementMatcher.Junction.AbstractBase<ClassLoader> classLoaderHasClassWithField(
      final String className, final String fieldName) {
    return new ClassLoaderHasClassWithFieldMatcher(className, fieldName);
  }

  public static ElementMatcher.Junction.AbstractBase<ClassLoader> classLoaderHasClassWithMethod(
      final String className, final String methodName, final String... methodArgs) {
    return new ClassLoaderHasClassWithMethodMatcher(className, methodName, methodArgs);
  }

  private static class SkipClassLoaderMatcher
      extends ElementMatcher.Junction.AbstractBase<ClassLoader> {
    public static final SkipClassLoaderMatcher INSTANCE = new SkipClassLoaderMatcher();
    /* Cache of classloader-instance -> (true|false). True = skip instrumentation. False = safe to instrument. */
    private static final WeakMap<ClassLoader, Boolean> SKIP_CACHE = newWeakMap();
    private static final Set<String> CLASSLOADER_CLASSES_TO_SKIP;

    static {
      final Set<String> classesToSkip = new HashSet<>();
      classesToSkip.add("org.codehaus.groovy.runtime.callsite.CallSiteClassLoader");
      classesToSkip.add("sun.reflect.DelegatingClassLoader");
      classesToSkip.add("jdk.internal.reflect.DelegatingClassLoader");
      classesToSkip.add("clojure.lang.DynamicClassLoader");
      classesToSkip.add("org.apache.cxf.common.util.ASMHelper$TypeHelperClassLoader");
      classesToSkip.add(DatadogClassLoader.class.getName());
      CLASSLOADER_CLASSES_TO_SKIP = Collections.unmodifiableSet(classesToSkip);
    }

    private SkipClassLoaderMatcher() {}

    @Override
    public boolean matches(final ClassLoader target) {
      if (target == BOOTSTRAP_CLASSLOADER) {
        // Don't skip bootstrap loader
        return false;
      }
      return shouldSkipClass(target) || shouldSkipInstance(target);
    }

    private boolean shouldSkipClass(final ClassLoader loader) {
      return CLASSLOADER_CLASSES_TO_SKIP.contains(loader.getClass().getName());
    }

    private boolean shouldSkipInstance(final ClassLoader loader) {
      Boolean cached = SKIP_CACHE.get(loader);
      if (null != cached) {
        return cached.booleanValue();
      }
      synchronized (this) {
        cached = SKIP_CACHE.get(loader);
        if (null != cached) {
          return cached.booleanValue();
        }
        final boolean skip = !delegatesToBootstrap(loader);
        if (skip) {
          log.debug(
              "skipping classloader instance {} of type {}", loader, loader.getClass().getName());
        }
        SKIP_CACHE.put(loader, skip);
        return skip;
      }
    }

    /**
     * TODO: this turns out to be useless with OSGi: {@code
     * }org.eclipse.osgi.internal.loader.BundleLoader#isRequestFromVM} returns {@code true} when
     * class loading is issued from this check and {@code false} for 'real' class loads. We should
     * come up with some sort of hack to avoid this problem.
     */
    private boolean delegatesToBootstrap(final ClassLoader loader) {
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

    private boolean loadsExpectedClass(final ClassLoader loader, final Class<?> expectedClass) {
      try {
        return loader.loadClass(expectedClass.getName()) == expectedClass;
      } catch (final ClassNotFoundException e) {
        return false;
      }
    }
  }

  private static class ClassLoaderNameMatcher
      extends ElementMatcher.Junction.AbstractBase<ClassLoader> {

    private final String name;

    private ClassLoaderNameMatcher(final String name) {
      this.name = name;
    }

    @Override
    public boolean matches(final ClassLoader target) {
      return target != null && name.equals(target.getClass().getName());
    }
  }

  public static class ClassLoaderHasClassMatcher
      extends ElementMatcher.Junction.AbstractBase<ClassLoader> {

    private final WeakMap<ClassLoader, Boolean> cache = newWeakMap();

    private final String[] names;

    private ClassLoaderHasClassMatcher(final String... names) {
      this.names = names;
    }

    @Override
    public boolean matches(final ClassLoader target) {
      if (target != null) {
        synchronized (target) {
          if (cache.containsKey(target)) {
            return cache.get(target);
          }
          for (final String name : names) {
            if (target.getResource(Utils.getResourceName(name)) == null) {
              cache.put(target, false);
              return false;
            }
          }
          cache.put(target, true);
          return true;
        }
      }
      return false;
    }
  }

  public static class ClassLoaderHasClassWithFieldMatcher
      extends ElementMatcher.Junction.AbstractBase<ClassLoader> {

    private final WeakMap<ClassLoader, Boolean> cache = newWeakMap();

    private final String className;
    private final String fieldName;

    private ClassLoaderHasClassWithFieldMatcher(final String className, final String fieldName) {
      this.className = className;
      this.fieldName = fieldName;
    }

    @Override
    public boolean matches(final ClassLoader target) {
      if (target != null) {
        synchronized (target) {
          if (cache.containsKey(target)) {
            return cache.get(target);
          }
          try {
            final Class<?> aClass = Class.forName(className, false, target);
            aClass.getDeclaredField(fieldName);
            cache.put(target, true);
            return true;
          } catch (final ClassNotFoundException e) {
            cache.put(target, false);
            return false;
          } catch (final NoSuchFieldException e) {
            cache.put(target, false);
            return false;
          }
        }
      }
      return false;
    }
  }

  public static class ClassLoaderHasClassWithMethodMatcher
      extends ElementMatcher.Junction.AbstractBase<ClassLoader> {

    private final WeakMap<ClassLoader, Boolean> cache = newWeakMap();

    private final String className;
    private final String methodName;
    private final String[] methodArgs;

    private ClassLoaderHasClassWithMethodMatcher(
        final String className, final String methodName, final String... methodArgs) {
      this.className = className;
      this.methodName = methodName;
      this.methodArgs = methodArgs;
    }

    @Override
    public boolean matches(final ClassLoader target) {
      if (target != null) {
        synchronized (target) {
          if (cache.containsKey(target)) {
            return cache.get(target);
          }
          try {
            final Class<?> aClass = Class.forName(className, false, target);
            final Class[] methodArgsClasses = new Class[methodArgs.length];
            for (int i = 0; i < methodArgs.length; ++i) {
              methodArgsClasses[i] = target.loadClass(methodArgs[i]);
            }
            if (aClass.isInterface()) {
              aClass.getMethod(methodName, methodArgsClasses);
            } else {
              aClass.getDeclaredMethod(methodName, methodArgsClasses);
            }
            cache.put(target, true);
            return true;
          } catch (final ClassNotFoundException e) {
            cache.put(target, false);
            return false;
          } catch (final NoSuchMethodException e) {
            cache.put(target, false);
            return false;
          }
        }
      }
      return false;
    }
  }
}
