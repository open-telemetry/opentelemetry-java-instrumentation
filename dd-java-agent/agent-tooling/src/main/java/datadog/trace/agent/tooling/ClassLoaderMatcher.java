package datadog.trace.agent.tooling;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import net.bytebuddy.matcher.ElementMatcher;

public class ClassLoaderMatcher {
  /** A private constructor that must not be invoked. */
  private ClassLoaderMatcher() {
    throw new UnsupportedOperationException();
  }

  public static ElementMatcher.Junction.AbstractBase<ClassLoader> classLoaderWithName(
      final String name) {
    return new ClassLoaderNameMatcher(name);
  }

  public static ElementMatcher.Junction.AbstractBase<ClassLoader> isReflectionClassLoader() {
    return new ClassLoaderNameMatcher("sun.reflect.DelegatingClassLoader");
  }

  public static ElementMatcher.Junction.AbstractBase<ClassLoader> classLoaderHasClasses(
      final String... names) {
    return new ClassLoaderHasClassMatcher(names);
  }

  public static ElementMatcher.Junction.AbstractBase<ClassLoader> classLoaderHasClassWithField(
      final String className, final String fieldName) {
    return new ClassLoaderHasClassWithFieldMatcher(className, fieldName);
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

    private final Map<ClassLoader, Boolean> cache =
        Collections.synchronizedMap(new WeakHashMap<ClassLoader, Boolean>());

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
          try {
            for (final String name : names) {
              Class.forName(name, false, target);
            }
            cache.put(target, true);
            return true;
          } catch (final ClassNotFoundException e) {
            cache.put(target, false);
            return false;
          }
        }
      }
      return false;
    }
  }

  public static class ClassLoaderHasClassWithFieldMatcher
      extends ElementMatcher.Junction.AbstractBase<ClassLoader> {

    private final Map<ClassLoader, Boolean> cache =
        Collections.synchronizedMap(new WeakHashMap<ClassLoader, Boolean>());

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
}
