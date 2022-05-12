/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.muzzle;

import static net.bytebuddy.matcher.ElementMatchers.declaresMethod;
import static net.bytebuddy.matcher.ElementMatchers.isAnnotatedWith;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.opentelemetry.test.AnnotatedTestClass;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.pool.TypePool;
import org.junit.jupiter.api.Test;

class AgentCachingPoolStrategyTest {

  @Test
  void testSkipResourceLookupForAnnotations() {
    ClassLoader classLoader =
        new ClassLoader(AgentCachingPoolStrategyTest.class.getClassLoader()) {

          private void checkResource(String name) {
            if (name.contains("TestAnnotation")) {
              throw new IllegalStateException("Unexpected resource lookup for " + name);
            }
          }

          @Override
          public URL getResource(String name) {
            checkResource(name);
            return super.getResource(name);
          }

          @Override
          public InputStream getResourceAsStream(String name) {
            checkResource(name);
            return super.getResourceAsStream(name);
          }

          @Override
          public Enumeration<URL> getResources(String name) throws IOException {
            checkResource(name);
            return super.getResources(name);
          }
        };

    ClassFileLocator locator = ClassFileLocator.ForClassLoader.of(classLoader);
    TypePool pool = AgentTooling.poolStrategy().typePool(locator, classLoader);
    TypePool.Resolution resolution = pool.describe(AnnotatedTestClass.class.getName());
    TypeDescription typeDescription = resolution.resolve();

    assertTrue(isAnnotatedWith(AnnotatedTestClass.TestAnnotation.class).matches(typeDescription));
    assertTrue(
        declaresMethod(isAnnotatedWith(AnnotatedTestClass.TestAnnotation.class))
            .matches(typeDescription));
  }
}
