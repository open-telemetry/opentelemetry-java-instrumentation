/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.extension.matcher;

import io.opentelemetry.instrumentation.api.internal.cache.Cache;
import io.opentelemetry.javaagent.bootstrap.internal.ClassLoaderMatcherCacheHolder;
import io.opentelemetry.javaagent.bootstrap.internal.InClassLoaderMatcher;
import java.util.BitSet;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import net.bytebuddy.matcher.ElementMatcher;

class ClassLoaderHasClassesNamedMatcher extends ElementMatcher.Junction.AbstractBase<ClassLoader> {
  // caching is disabled for build time muzzle checks
  // this field is set via reflection from ClassLoaderMatcher
  static boolean useCache = true;
  private static final AtomicInteger counter = new AtomicInteger();

  private final String[] resources;
  private final int index = counter.getAndIncrement();

  ClassLoaderHasClassesNamedMatcher(String... classNames) {
    resources = classNames;
    for (int i = 0; i < resources.length; i++) {
      resources[i] = resources[i].replace(".", "/") + ".class";
    }
    if (useCache) {
      Manager.INSTANCE.add(this);
    }
  }

  @Override
  public boolean matches(ClassLoader cl) {
    if (cl == null) {
      // Can't match the bootstrap class loader.
      return false;
    }
    if (useCache) {
      return Manager.INSTANCE.match(this, cl);
    } else {
      return hasResources(cl, resources);
    }
  }

  private static boolean hasResources(ClassLoader cl, String... resources) {
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

  private static class Manager {
    private static final BitSet EMPTY = new BitSet(0);
    static final Manager INSTANCE = new Manager();
    private final List<ClassLoaderHasClassesNamedMatcher> matchers = new CopyOnWriteArrayList<>();
    private final Cache<ClassLoader, BitSet> enabled = Cache.weak();
    private volatile boolean matchCalled = false;

    Manager() {
      ClassLoaderMatcherCacheHolder.addCache(enabled);
    }

    void add(ClassLoaderHasClassesNamedMatcher matcher) {
      if (matchCalled) {
        throw new IllegalStateException("All matchers should be create before match is called");
      }
      matchers.add(matcher);
    }

    boolean match(ClassLoaderHasClassesNamedMatcher matcher, ClassLoader cl) {
      matchCalled = true;
      BitSet set = enabled.get(cl);
      if (set == null) {
        set = new BitSet(counter.get());
        for (ClassLoaderHasClassesNamedMatcher m : matchers) {
          if (hasResources(cl, m.resources)) {
            set.set(m.index);
          }
        }
        enabled.put(cl, set.isEmpty() ? EMPTY : set);
      } else if (set.isEmpty()) {
        return false;
      }
      return set.get(matcher.index);
    }
  }
}
