/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.extension.matcher;

import io.opentelemetry.instrumentation.api.caching.Cache;
import io.opentelemetry.javaagent.bootstrap.ClassLoaderMatcherCacheHolder;
import io.opentelemetry.javaagent.instrumentation.api.internal.InClassLoaderMatcher;
import net.bytebuddy.matcher.ElementMatcher;
import org.checkerframework.checker.nullness.qual.Nullable;

public final class ClassLoaderMatcher {

  @Nullable public static final ClassLoader BOOTSTRAP_CLASSLOADER = null;

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

    private final Cache<ClassLoader, Boolean> cache =
        Cache.newBuilder().setWeakKeys().setMaximumSize(25).build();

    private final String[] resources;

    private ClassLoaderHasClassesNamedMatcher(String... classNames) {
      resources = classNames;
      for (int i = 0; i < resources.length; i++) {
        resources[i] = resources[i].replace(".", "/") + ".class";
      }
      ClassLoaderMatcherCacheHolder.addCache(cache);
    }

    private boolean hasResources(ClassLoader cl) {
      boolean priorValue = InClassLoaderMatcher.getAndSet(true);
      try {
        for (String resource : resources) {
          if (cl.getResource(resource) == null) {
            return false;
          }
        }
      } finally {
        InClassLoaderMatcher.set(priorValue);
      }
      return true;
    }

    @Override
    public boolean matches(ClassLoader cl) {
      if (cl == BOOTSTRAP_CLASSLOADER) {
        // Can't match the bootstrap classloader.
        return false;
      }
      return cache.computeIfAbsent(cl, this::hasResources);
    }
  }
}
