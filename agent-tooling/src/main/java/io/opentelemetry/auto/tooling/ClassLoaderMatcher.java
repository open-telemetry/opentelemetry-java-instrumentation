/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.auto.tooling;

import io.opentelemetry.auto.bootstrap.PatchLogger;
import io.opentelemetry.auto.bootstrap.WeakCache;
import net.bytebuddy.matcher.ElementMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ClassLoaderMatcher {

  private static final Logger log = LoggerFactory.getLogger(ClassLoaderMatcher.class);

  public static final ClassLoader BOOTSTRAP_CLASSLOADER = null;

  /** A private constructor that must not be invoked. */
  private ClassLoaderMatcher() {
    throw new UnsupportedOperationException();
  }

  public static ElementMatcher.Junction.AbstractBase<ClassLoader> skipClassLoader() {
    return SkipClassLoaderMatcher.INSTANCE;
  }

  /**
   * NOTICE: Does not match the bootstrap classpath. Don't use with classes expected to be on the
   * bootstrap.
   *
   * @param classNames list of names to match. returns true if empty.
   * @return true if class is available as a resource and not the bootstrap classloader.
   */
  public static ElementMatcher.Junction.AbstractBase<ClassLoader> hasClassesNamed(
      final String... classNames) {
    return new ClassLoaderHasClassesNamedMatcher(classNames);
  }

  private static class SkipClassLoaderMatcher
      extends ElementMatcher.Junction.AbstractBase<ClassLoader> {
    public static final SkipClassLoaderMatcher INSTANCE = new SkipClassLoaderMatcher();
    /* Cache of classloader-instance -> (true|false). True = skip instrumentation. False = safe to instrument. */
    private static final String AGENT_CLASSLOADER_NAME =
        "io.opentelemetry.auto.bootstrap.AgentClassLoader";
    private static final String EXPORTER_CLASSLOADER_NAME =
        "io.opentelemetry.auto.tooling.ExporterClassLoader";
    private static final WeakCache<ClassLoader, Boolean> skipCache = AgentTooling.newWeakCache();

    private SkipClassLoaderMatcher() {}

    @Override
    public boolean matches(final ClassLoader cl) {
      if (cl == BOOTSTRAP_CLASSLOADER) {
        // Don't skip bootstrap loader
        return false;
      }
      Boolean v = skipCache.getIfPresent(cl);
      if (v != null) {
        return v;
      }
      // when ClassloadingInstrumentation is active, checking delegatesToBootstrap() below is not
      // required, because ClassloadingInstrumentation forces all class loaders to load all of the
      // classes in Constants.BOOTSTRAP_PACKAGE_PREFIXES directly from the bootstrap class loader
      //
      // however, at this time we don't want to introduce the concept of a required instrumentation,
      // and we don't want to introduce the concept of the tooling code depending on whether or not
      // a particular instrumentation is active (mainly because this particular use case doesn't
      // seem to justify introducing either of these new concepts)
      v = shouldSkipClass(cl) || !delegatesToBootstrap(cl);
      skipCache.put(cl, v);
      return v;
    }

    private static boolean shouldSkipClass(final ClassLoader loader) {
      switch (loader.getClass().getName()) {
        case "org.codehaus.groovy.runtime.callsite.CallSiteClassLoader":
        case "sun.reflect.DelegatingClassLoader":
        case "jdk.internal.reflect.DelegatingClassLoader":
        case "clojure.lang.DynamicClassLoader":
        case "org.apache.cxf.common.util.ASMHelper$TypeHelperClassLoader":
        case "sun.misc.Launcher$ExtClassLoader":
        case AGENT_CLASSLOADER_NAME:
        case EXPORTER_CLASSLOADER_NAME:
          return true;
      }
      return false;
    }

    /**
     * TODO: this turns out to be useless with OSGi: {@code
     * org.eclipse.osgi.internal.loader.BundleLoader#isRequestFromVM} returns {@code true} when
     * class loading is issued from this check and {@code false} for 'real' class loads. We should
     * come up with some sort of hack to avoid this problem.
     */
    private static boolean delegatesToBootstrap(final ClassLoader loader) {
      boolean delegates = true;
      if (!loadsExpectedClass(loader, PatchLogger.class)) {
        log.debug("loader {} failed to delegate bootstrap agent class", loader);
        delegates = false;
      }
      return delegates;
    }

    private static boolean loadsExpectedClass(
        final ClassLoader loader, final Class<?> expectedClass) {
      try {
        return loader.loadClass(expectedClass.getName()) == expectedClass;
      } catch (final ClassNotFoundException e) {
        return false;
      }
    }
  }

  private static class ClassLoaderHasClassesNamedMatcher
      extends ElementMatcher.Junction.AbstractBase<ClassLoader> {

    private final WeakCache<ClassLoader, Boolean> cache = AgentTooling.newWeakCache(25);

    private final String[] resources;

    private ClassLoaderHasClassesNamedMatcher(final String... classNames) {
      resources = classNames;
      for (int i = 0; i < resources.length; i++) {
        resources[i] = resources[i].replace(".", "/") + ".class";
      }
    }

    private boolean hasResources(final ClassLoader cl) {
      for (String resource : resources) {
        if (cl.getResource(resource) == null) {
          return false;
        }
      }
      return true;
    }

    @Override
    public boolean matches(final ClassLoader cl) {
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
