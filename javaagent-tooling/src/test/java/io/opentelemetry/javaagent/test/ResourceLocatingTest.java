/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.test;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.javaagent.tooling.bytebuddy.AgentLocationStrategy;
import java.net.URL;
import java.util.concurrent.atomic.AtomicReference;
import net.bytebuddy.dynamic.ClassFileLocator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ResourceLocatingTest {

  private static final AtomicReference<String> lastLookup = new AtomicReference<>();

  private static final ClassLoader childLoader =
      new ClassLoader(ResourceLocatingTest.class.getClassLoader()) {
        @Override
        public URL getResource(String name) {
          lastLookup.set(name);
          // do not delegate resource lookup
          return findResource(name);
        }
      };

  @AfterEach
  void cleanup() {
    lastLookup.set(null);
  }

  @Test
  void findsResourcesFromParentClassloader() throws Exception {
    ClassFileLocator locator = new AgentLocationStrategy(null).classFileLocator(childLoader, null);
    assertThat(locator.locate("java/lang/Object").isResolved()).isTrue();
    assertThat(lastLookup).hasValue("java/lang/Object.class");

    assertThat(locator.locate("java/lang/InvalidClass").isResolved()).isFalse();
    assertThat(lastLookup).hasValue("java/lang/InvalidClass.class");
  }
}
