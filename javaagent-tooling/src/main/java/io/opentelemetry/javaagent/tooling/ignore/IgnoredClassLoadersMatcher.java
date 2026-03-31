/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.ignore;

import static java.util.logging.Level.FINE;

import io.opentelemetry.instrumentation.api.internal.cache.Cache;
import io.opentelemetry.javaagent.bootstrap.PatchLogger;
import io.opentelemetry.javaagent.tooling.util.Trie;
import java.util.logging.Logger;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.matcher.ElementMatcher;

public class IgnoredClassLoadersMatcher extends ElementMatcher.Junction.AbstractBase<ClassLoader> {
  private static final Logger logger = Logger.getLogger(IgnoredClassLoadersMatcher.class.getName());

  /* Cache of class loader instance -> (true|false). True = skip instrumentation. False = safe to instrument. */
  private static final Cache<ClassLoader, Boolean> skipCache = Cache.weak();

  private final Trie<IgnoreAllow> ignoredClassLoaders;

  public IgnoredClassLoadersMatcher(Trie<IgnoreAllow> ignoredClassLoaders) {
    this.ignoredClassLoaders = ignoredClassLoaders;
  }

  @Override
  public boolean matches(ClassLoader cl) {
    if (cl == ClassLoadingStrategy.BOOTSTRAP_LOADER) {
      // Don't skip bootstrap loader
      return false;
    }

    String name = cl.getClass().getName();

    IgnoreAllow ignored = ignoredClassLoaders.getOrNull(name);
    if (ignored == IgnoreAllow.ALLOW) {
      return false;
    } else if (ignored == IgnoreAllow.IGNORE) {
      return true;
    }

    // Ignore class loader if it doesn't delegate to bootstrap class loader. This could happen when
    // class loader instrumentation is disabled or the class loader class itself is excluded from
    // instrumentation.
    // We are not using skipCache.computeIfAbsent here because computeIfAbsent takes a lock. Calling
    // loadClass with that lock held can cause deadlocks. See discussion for more details:
    // https://github.com/open-telemetry/opentelemetry-java-instrumentation/discussions/17224
    Boolean result = skipCache.get(cl);
    if (result == null) {
      result = !delegatesToBootstrap(cl);
      skipCache.put(cl, result);
    }
    return result;
  }

  /**
   * TODO: this turns out to be useless with OSGi: {@code
   * org.eclipse.osgi.internal.loader.BundleLoader#isRequestFromVM} returns {@code true} when class
   * loading is issued from this check and {@code false} for 'real' class loads. We should come up
   * with some sort of hack to avoid this problem.
   */
  private static boolean delegatesToBootstrap(ClassLoader loader) {
    boolean delegates = true;
    if (!loadsExpectedClass(loader, PatchLogger.class)) {
      logger.log(FINE, "loader {0} failed to delegate bootstrap agent class", loader);
      delegates = false;
    }
    return delegates;
  }

  private static boolean loadsExpectedClass(ClassLoader loader, Class<?> expectedClass) {
    try {
      return loader.loadClass(expectedClass.getName()) == expectedClass;
    } catch (ClassNotFoundException e) {
      return false;
    }
  }
}
