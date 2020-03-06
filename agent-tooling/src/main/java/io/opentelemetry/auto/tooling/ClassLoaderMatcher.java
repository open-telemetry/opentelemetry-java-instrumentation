/*
 * Copyright 2020, OpenTelemetry Authors
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

import static io.opentelemetry.auto.bootstrap.WeakMap.Provider.newWeakMap;

import io.opentelemetry.auto.bootstrap.WeakMap;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.matcher.ElementMatcher;

@Slf4j
public class ClassLoaderMatcher {
  public static final ClassLoader BOOTSTRAP_CLASSLOADER = null;

  /** A private constructor that must not be invoked. */
  private ClassLoaderMatcher() {
    throw new UnsupportedOperationException();
  }

  public static ElementMatcher.Junction.AbstractBase<ClassLoader> skipClassLoader() {
    return SkipClassLoaderMatcher.INSTANCE;
  }

  public static ElementMatcher.Junction.AbstractBase<ClassLoader> classLoaderHasClasses(
      final String... names) {
    return new ClassLoaderHasClassMatcher(names);
  }

  private static class SkipClassLoaderMatcher
      extends ElementMatcher.Junction.AbstractBase<ClassLoader> {
    public static final SkipClassLoaderMatcher INSTANCE = new SkipClassLoaderMatcher();
    private static final String AGENT_CLASSLOADER_NAME =
        "io.opentelemetry.auto.bootstrap.AgentClassLoader";

    private SkipClassLoaderMatcher() {}

    @Override
    public boolean matches(final ClassLoader target) {
      if (target == BOOTSTRAP_CLASSLOADER) {
        // Don't skip bootstrap loader
        return false;
      }
      return shouldSkipClass(target);
    }

    private boolean shouldSkipClass(final ClassLoader loader) {
      switch (loader.getClass().getName()) {
        case "org.codehaus.groovy.runtime.callsite.CallSiteClassLoader":
        case "sun.reflect.DelegatingClassLoader":
        case "jdk.internal.reflect.DelegatingClassLoader":
        case "clojure.lang.DynamicClassLoader":
        case "org.apache.cxf.common.util.ASMHelper$TypeHelperClassLoader":
        case "sun.misc.Launcher$ExtClassLoader":
        case AGENT_CLASSLOADER_NAME:
          return true;
      }
      return false;
    }
  }

  public static class ClassLoaderHasClassMatcher
      extends ElementMatcher.Junction.AbstractBase<ClassLoader>
      implements WeakMap.ValueSupplier<ClassLoader, Boolean> {

    private final WeakMap<ClassLoader, Boolean> cache = newWeakMap();

    private final String[] names;

    private ClassLoaderHasClassMatcher(final String... names) {
      this.names = names;
    }

    @Override
    public boolean matches(final ClassLoader target) {
      if (target != null) {
        return cache.computeIfAbsent(target, this);
      }

      return false;
    }

    @Override
    public Boolean get(final ClassLoader target) {
      for (final String name : names) {
        if (target.getResource(Utils.getResourceName(name)) == null) {
          return false;
        }
      }

      return true;
    }
  }
}
