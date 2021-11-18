/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.ignore;

import io.opentelemetry.instrumentation.api.cache.Cache;
import io.opentelemetry.javaagent.bootstrap.PatchLogger;
import io.opentelemetry.javaagent.tooling.util.Trie;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.matcher.ElementMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IgnoredClassLoadersMatcher extends ElementMatcher.Junction.AbstractBase<ClassLoader> {
  private static final Logger logger = LoggerFactory.getLogger(IgnoredClassLoadersMatcher.class);

  /* Cache of classloader-instance -> (true|false). True = skip instrumentation. False = safe to instrument. */
  private static final Cache<ClassLoader, Boolean> skipCache =
      Cache.builder().build();

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

    return skipCache.computeIfAbsent(
        cl,
        c -> {
          // when ClassloadingInstrumentation is active, checking delegatesToBootstrap() below is
          // not
          // required, because ClassloadingInstrumentation forces all class loaders to load all of
          // the
          // classes in Constants.BOOTSTRAP_PACKAGE_PREFIXES directly from the bootstrap class
          // loader
          //
          // however, at this time we don't want to introduce the concept of a required
          // instrumentation,
          // and we don't want to introduce the concept of the tooling code depending on whether or
          // not
          // a particular instrumentation is active (mainly because this particular use case doesn't
          // seem to justify introducing either of these new concepts)
          return !delegatesToBootstrap(cl);
        });
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
      logger.debug("loader {} failed to delegate bootstrap agent class", loader);
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
