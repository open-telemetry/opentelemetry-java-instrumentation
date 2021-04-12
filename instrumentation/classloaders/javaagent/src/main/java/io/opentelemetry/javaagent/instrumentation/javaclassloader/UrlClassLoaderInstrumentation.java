/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.javaclassloader;

import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isProtected;
import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.bootstrap.ClassLoaderMatcherCacheHolder;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class UrlClassLoaderInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("java.net.URLClassLoader");
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod()
            .and(named("addURL"))
            .and(takesArguments(1))
            .and(takesArgument(0, URL.class))
            .and(isProtected())
            .and(not(isStatic())),
        UrlClassLoaderInstrumentation.class.getName() + "$InvalidateClassLoaderMatcher");
  }

  public static class InvalidateClassLoaderMatcher {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(@Advice.This URLClassLoader loader) {
      ClassLoaderMatcherCacheHolder.invalidateAllCachesForClassLoader(loader);
    }
  }
}
