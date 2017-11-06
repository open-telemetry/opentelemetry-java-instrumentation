package dd.trace;

import net.bytebuddy.matcher.ElementMatcher;

public class ClassLoaderHasClassWithFieldMatcher
    extends ElementMatcher.Junction.AbstractBase<ClassLoader> {

  private final String className;
  private final String fieldName;

  private ClassLoaderHasClassWithFieldMatcher(final String className, final String fieldName) {
    this.className = className;
    this.fieldName = fieldName;
  }

  public static AbstractBase<ClassLoader> classLoaderHasClassWithField(
      final String className, final String fieldName) {
    return new ClassLoaderHasClassWithFieldMatcher(className, fieldName);
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
