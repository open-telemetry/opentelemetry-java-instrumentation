package com.datadoghq.agent.utils;

import net.bytebuddy.matcher.ElementMatcher;

// Borrowed from https://github.com/stagemonitor/stagemonitor/blob/master/stagemonitor-core/src/main/java/org/stagemonitor/core/instrument/ClassLoaderNameMatcher.java
public class ClassLoaderNameMatcher extends ElementMatcher.Junction.AbstractBase<ClassLoader> {

  private final String name;

  private ClassLoaderNameMatcher(final String name) {
    this.name = name;
  }

  public static ElementMatcher.Junction.AbstractBase<ClassLoader> classLoaderWithName(
      final String name) {
    return new ClassLoaderNameMatcher(name);
  }

  public static ElementMatcher.Junction.AbstractBase<ClassLoader> isReflectionClassLoader() {
    return new ClassLoaderNameMatcher("sun.reflect.DelegatingClassLoader");
  }

  @Override
  public boolean matches(final ClassLoader target) {
    return target != null && name.equals(target.getClass().getName());
  }
}
