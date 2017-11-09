package dd.trace;

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

    private final String[] names;

    private ClassLoaderHasClassMatcher(final String... names) {
      this.names = names;
    }

    @Override
    public boolean matches(final ClassLoader target) {
      try {
        if (target != null) {
          for (final String name : names) {
            Class.forName(name, false, target);
          }
          return true;
        }
        return false;
      } catch (final ClassNotFoundException e) {
        return false;
      }
    }
  }

  public static class ClassLoaderHasClassWithFieldMatcher
      extends ElementMatcher.Junction.AbstractBase<ClassLoader> {

    private final String className;
    private final String fieldName;

    private ClassLoaderHasClassWithFieldMatcher(final String className, final String fieldName) {
      this.className = className;
      this.fieldName = fieldName;
    }

    @Override
    public boolean matches(final ClassLoader target) {
      try {
        if (target != null) {
          final Class<?> aClass = Class.forName(className, false, target);
          aClass.getDeclaredField(fieldName);
          return true;
        }
        return false;
      } catch (final ClassNotFoundException e) {
        return false;
      } catch (final NoSuchFieldException e) {
        return false;
      }
    }
  }
}
