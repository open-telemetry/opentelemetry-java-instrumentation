/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.bytebuddy;

import io.opentelemetry.javaagent.tooling.Utils;
import java.util.ArrayList;
import java.util.List;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.utility.JavaModule;

/**
 * Locate resources with the loading classloader. Because of a quirk with the way classes appended
 * to the bootstrap classpath work, we first check our bootstrap proxy. If the loading classloader
 * cannot find the desired resource, check up the classloader hierarchy until a resource is found.
 */
public class AgentLocationStrategy implements AgentBuilder.LocationStrategy {
  public ClassFileLocator classFileLocator(ClassLoader classLoader) {
    return classFileLocator(classLoader, null);
  }

  @Override
  public ClassFileLocator classFileLocator(ClassLoader classLoader, JavaModule javaModule) {
    List<ClassFileLocator> locators = new ArrayList<>();
    locators.add(ClassFileLocator.ForClassLoader.of(Utils.getBootstrapProxy()));
    while (classLoader != null) {
      locators.add(ClassFileLocator.ForClassLoader.WeaklyReferenced.of(classLoader));
      classLoader = classLoader.getParent();
    }
    return new ClassFileLocator.Compound(locators.toArray(new ClassFileLocator[0]));
  }
}
