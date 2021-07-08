package io.opentelemetry.javaagent.muzzle.generation.collector;

public class Utils {

  /** com/foo/Bar to com.foo.Bar */
  public static String getClassName(String internalName) {
    return internalName.replace('/', '.');
  }

  /** com.foo.Bar to com/foo/Bar */
  public static String getInternalName(Class<?> clazz) {
    return clazz.getName().replace('.', '/');
  }

  /** com.foo.Bar to com/foo/Bar.class */
  public static String getResourceName(String className) {
    return className.replace('.', '/') + ".class";
  }

}
