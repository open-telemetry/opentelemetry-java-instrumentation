/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.muzzle;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.utility.JavaModule;

/**
 * Locate resources with the loading class loader. Because of a quirk with the way classes appended
 * to the bootstrap classpath work, we first check our bootstrap proxy. If the loading class loader
 * cannot find the desired resource, check up the class loader hierarchy until a resource is found.
 */
public class AgentLocationStrategy implements AgentBuilder.LocationStrategy {

  @Nullable private final ClassLoader bootstrapProxy;
  private final List<ClassFileLocator> additionalLocators;

  public AgentLocationStrategy(
      @Nullable ClassLoader bootstrapProxy, List<ClassFileLocator> additionalLocators) {
    this.bootstrapProxy = bootstrapProxy;
    this.additionalLocators = additionalLocators;
  }

  public ClassFileLocator classFileLocator(ClassLoader classLoader) {
    return classFileLocator(classLoader, null);
  }

  @Override
  public ClassFileLocator classFileLocator(
      @Nullable ClassLoader classLoader, @Nullable JavaModule javaModule) {
    List<ClassFileLocator> locators = new ArrayList<>();

    if (additionalLocators != null) {
      locators.addAll(additionalLocators);
    }
    if (classLoader != null) {
      locators.add(ClassFileLocator.ForClassLoader.WeaklyReferenced.of(classLoader));
    }
    // can be null in unit tests
    if (bootstrapProxy != null) {
      locators.add(ClassFileLocator.ForClassLoader.of(bootstrapProxy));
    }

    return new ClassFileLocator.Compound(locators);
  }
}
