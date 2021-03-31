/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.bytebuddy.matcher;

import static java.util.Arrays.asList;

import io.opentelemetry.instrumentation.api.caching.Cache;
import io.opentelemetry.javaagent.instrumentation.api.internal.InClassLoaderMatcher;
import io.opentelemetry.javaagent.tooling.Utils;
import java.util.List;
import java.util.stream.Collectors;
import net.bytebuddy.matcher.ElementMatcher;

public final class ClassLoaderMatcher {

  public static final ClassLoader BOOTSTRAP_CLASSLOADER = null;

  /** A private constructor that must not be invoked. */
  private ClassLoaderMatcher() {
    throw new UnsupportedOperationException();
  }

  /**
   * Creates a matcher that checks if all of the passed classes are in the passed {@link
   * ClassLoader}. If {@code classNames} is empty the matcher will always return {@code true}.
   *
   * <p>NOTICE: Does not match the bootstrap classpath. Don't use with classes expected to be on the
   * bootstrap.
   */
  public static ElementMatcher.Junction<ClassLoader> hasClassesNamed(String... classNames) {
    return new ClassLoaderHasAllClassesNamedMatcher(asList(classNames));
  }

  /**
   * Creates a matcher that checks if any of the passed classes are in the passed {@link
   * ClassLoader}. If {@code classNames} is empty the matcher will always return {@code false}.
   *
   * <p>NOTICE: Does not match the bootstrap classpath. Don't use with classes expected to be on the
   * bootstrap.
   */
  public static ElementMatcher.Junction<ClassLoader> hasAnyClassesNamed(List<String> classNames) {
    return new ClassLoaderHasAnyClassesNamedMatcher(classNames);
  }

  private abstract static class AbstractClassLoaderMatcher
      extends ElementMatcher.Junction.AbstractBase<ClassLoader> {

    private final Cache<ClassLoader, Boolean> cache =
        Cache.newBuilder().setWeakKeys().setMaximumSize(25).build();

    protected final List<String> resources;

    private AbstractClassLoaderMatcher(List<String> classNames) {
      resources = classNames.stream().map(Utils::getResourceName).collect(Collectors.toList());
    }

    protected abstract boolean doMatch(ClassLoader cl);

    @Override
    public boolean matches(ClassLoader cl) {
      if (cl == BOOTSTRAP_CLASSLOADER) {
        // Can't match the bootstrap classloader.
        return false;
      }
      return cache.computeIfAbsent(cl, this::hasResources);
    }

    private boolean hasResources(ClassLoader cl) {
      boolean priorValue = InClassLoaderMatcher.getAndSet(true);
      boolean value;
      try {
        value = doMatch(cl);
      } finally {
        InClassLoaderMatcher.set(priorValue);
      }
      return value;
    }
  }

  private static class ClassLoaderHasAllClassesNamedMatcher extends AbstractClassLoaderMatcher {
    private ClassLoaderHasAllClassesNamedMatcher(List<String> classNames) {
      super(classNames);
    }

    @Override
    protected boolean doMatch(ClassLoader cl) {
      for (String resource : resources) {
        if (cl.getResource(resource) == null) {
          return false;
        }
      }
      return true;
    }
  }

  private static class ClassLoaderHasAnyClassesNamedMatcher extends AbstractClassLoaderMatcher {
    private ClassLoaderHasAnyClassesNamedMatcher(List<String> classNames) {
      super(classNames);
    }

    @Override
    protected boolean doMatch(ClassLoader cl) {
      for (String resource : resources) {
        if (cl.getResource(resource) != null) {
          return true;
        }
      }
      return false;
    }
  }
}
