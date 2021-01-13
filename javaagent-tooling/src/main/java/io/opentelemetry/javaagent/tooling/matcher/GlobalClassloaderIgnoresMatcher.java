/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.matcher;

import io.opentelemetry.javaagent.bootstrap.PatchLogger;
import io.opentelemetry.javaagent.bootstrap.WeakCache;
import io.opentelemetry.javaagent.spi.IgnoreMatcherProvider;
import io.opentelemetry.javaagent.tooling.AgentTooling;
import io.opentelemetry.javaagent.tooling.bytebuddy.matcher.ClassLoaderMatcher;
import net.bytebuddy.matcher.ElementMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GlobalClassloaderIgnoresMatcher
    extends ElementMatcher.Junction.AbstractBase<ClassLoader> {
  private static final Logger log = LoggerFactory.getLogger(GlobalClassloaderIgnoresMatcher.class);

  /* Cache of classloader-instance -> (true|false). True = skip instrumentation. False = safe to instrument. */
  private static final String AGENT_CLASSLOADER_NAME =
      "io.opentelemetry.javaagent.bootstrap.AgentClassLoader";
  private static final String EXPORTER_CLASSLOADER_NAME =
      "io.opentelemetry.javaagent.tooling.ExporterClassLoader";
  private static final WeakCache<ClassLoader, Boolean> skipCache = AgentTooling.newWeakCache();

  public static ElementMatcher.Junction.AbstractBase<ClassLoader> skipClassLoader(
      IgnoreMatcherProvider ignoreMatcherProvider) {
    return new GlobalClassloaderIgnoresMatcher(ignoreMatcherProvider);
  }

  private final IgnoreMatcherProvider ignoreMatcherProviders;

  private GlobalClassloaderIgnoresMatcher(IgnoreMatcherProvider ignoreMatcherProviders) {
    this.ignoreMatcherProviders = ignoreMatcherProviders;
  }

  @Override
  public boolean matches(ClassLoader cl) {
    IgnoreMatcherProvider.Result ignoreResult = ignoreMatcherProviders.classloader(cl);
    switch (ignoreResult) {
      case IGNORE:
        return true;
      case ALLOW:
        return false;
      case DEFAULT:
      default:
    }

    if (cl == ClassLoaderMatcher.BOOTSTRAP_CLASSLOADER) {
      // Don't skip bootstrap loader
      return false;
    }
    if (canSkipClassLoaderByName(cl)) {
      return true;
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
    v = !delegatesToBootstrap(cl);
    skipCache.put(cl, v);
    return v;
  }

  private static boolean canSkipClassLoaderByName(ClassLoader loader) {
    String name = loader.getClass().getName();
    // check by FQCN
    switch (name) {
      case "org.codehaus.groovy.runtime.callsite.CallSiteClassLoader":
      case "sun.reflect.DelegatingClassLoader":
      case "jdk.internal.reflect.DelegatingClassLoader":
      case "clojure.lang.DynamicClassLoader":
      case "org.apache.cxf.common.util.ASMHelper$TypeHelperClassLoader":
      case "sun.misc.Launcher$ExtClassLoader":
      case AGENT_CLASSLOADER_NAME:
      case EXPORTER_CLASSLOADER_NAME:
        return true;
      default:
        // noop
    }
    // check by package prefix
    if (name.startsWith("datadog.")
        || name.startsWith("com.dynatrace.")
        || name.startsWith("com.appdynamics.")
        || name.startsWith("com.newrelic.agent.")
        || name.startsWith("com.newrelic.api.agent.")
        || name.startsWith("com.nr.agent.")) {
      return true;
    }
    return false;
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
      log.debug("loader {} failed to delegate bootstrap agent class", loader);
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
