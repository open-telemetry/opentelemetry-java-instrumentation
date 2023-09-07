/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.extension.matcher;

import io.opentelemetry.instrumentation.api.internal.cache.Cache;
import io.opentelemetry.javaagent.bootstrap.internal.ClassLoaderMatcherCacheHolder;
import io.opentelemetry.javaagent.bootstrap.internal.InClassLoaderMatcher;
import java.util.BitSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import net.bytebuddy.matcher.ElementMatcher;

class ClassLoaderHasClassesNamedMatcher extends ElementMatcher.Junction.AbstractBase<ClassLoader> {
  // caching is disabled for build time muzzle checks
  // this field is set via reflection from ClassLoaderMatcher
  static boolean useCache = true;
  private static final AtomicInteger counter = new AtomicInteger();

  private final String[] resources;
  // each matcher gets a unique index that is used for caching the matching status
  private final int index = counter.getAndIncrement();

  ClassLoaderHasClassesNamedMatcher(String... classNames) {
    resources = classNames;
    for (int i = 0; i < resources.length; i++) {
      resources[i] = resources[i].replace(".", "/") + ".class";
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
    static final Manager INSTANCE = new Manager();
    // each matcher gets a two bits in BitSet, that first bit indicates whether current matcher has
    // been run for given class loader and the second whether it matched or not
    private final Cache<ClassLoader, BitSet> enabled = Cache.weak();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Lock readLock = lock.readLock();
    private final Lock writeLock = lock.writeLock();

    Manager() {
      ClassLoaderMatcherCacheHolder.addCache(enabled);
    }

    boolean match(ClassLoaderHasClassesNamedMatcher matcher, ClassLoader cl) {
      BitSet set = enabled.computeIfAbsent(cl, (unused) -> new BitSet(counter.get() * 2));
      int matcherRunBit = 2 * matcher.index;
      int matchedBit = matcherRunBit + 1;
      readLock.lock();
      try {
        if (!set.get(matcherRunBit)) {
          // read lock needs to be released before upgrading to write lock
          readLock.unlock();
          // we do the resource presence check outside the lock to keep the time we need to hold
          // the write lock minimal
          boolean matches = hasResources(cl, matcher.resources);
          writeLock.lock();
          try {
            if (!set.get(matcherRunBit)) {
              if (matches) {
                set.set(matchedBit);
              }
              set.set(matcherRunBit);
            }
          } finally {
            // downgrading the write lock to the read lock
            readLock.lock();
            writeLock.unlock();
          }
        }

        return set.get(matchedBit);
      } finally {
        readLock.unlock();
      }
    }
  }
}
