package dd.trace;

import net.bytebuddy.matcher.ElementMatcher;

public class ClassLoaderHasClassMatcher extends ElementMatcher.Junction.AbstractBase<ClassLoader> {

  private final String[] names;

  private ClassLoaderHasClassMatcher(final String... names) {
    this.names = names;
  }

  public static ElementMatcher.Junction.AbstractBase<ClassLoader> classLoaderHasClasses(
      final String... names) {
    return new ClassLoaderHasClassMatcher(names);
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
