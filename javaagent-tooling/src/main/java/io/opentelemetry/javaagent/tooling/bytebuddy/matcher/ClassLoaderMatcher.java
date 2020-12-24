/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.bytebuddy.matcher;

import io.opentelemetry.javaagent.bootstrap.WeakCache;
import io.opentelemetry.javaagent.tooling.AgentTooling;
import net.bytebuddy.matcher.ElementMatcher;

public final class ClassLoaderMatcher {

  public static final ClassLoader BOOTSTRAP_CLASSLOADER = null;

  /** A private constructor that must not be invoked. */
  private ClassLoaderMatcher() {
    throw new UnsupportedOperationException();
  }

  /**
   * NOTICE: Does not match the bootstrap classpath. Don't use with classes expected to be on the
   * bootstrap.
   *
   * @param classNames list of names to match. returns true if empty.
   * @return true if class is available as a resource and not the bootstrap classloader.
   */
  public static ElementMatcher.Junction.AbstractBase<ClassLoader> hasClassesNamed(
      String... classNames) {
    return new ClassLoaderHasClassesNamedMatcher(classNames);
  }

  private static class ClassLoaderHasClassesNamedMatcher
      extends ElementMatcher.Junction.AbstractBase<ClassLoader> {

    private final WeakCache<ClassLoader, Boolean> cache = AgentTooling.newWeakCache(25);

    private final String[] resources;

    private ClassLoaderHasClassesNamedMatcher(String... classNames) {
      resources = classNames;
      for (int i = 0; i < resources.length; i++) {
        resources[i] = resources[i].replace(".", "/") + ".class";
      }
    }

    private boolean hasResources(ClassLoader cl) {
      for (String resource : resources) {
        if (cl.getResource(resource) == null) {
          return false;
        }
      }
      return true;
    }

    @Override
    public boolean matches(ClassLoader cl) {
      if (cl == BOOTSTRAP_CLASSLOADER) {
        // Can't match the bootstrap classloader.
        return false;
      }
      Boolean cached;
      if ((cached = cache.getIfPresent(cl)) != null) {
        return cached;
      }
      boolean value = hasResources(cl);
      cache.put(cl, value);
      return value;
    }
  }
}
