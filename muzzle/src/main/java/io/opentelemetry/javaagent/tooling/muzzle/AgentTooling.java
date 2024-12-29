/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.muzzle;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceLoader;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.utility.JavaModule;

/**
 * This class contains class references for objects shared by the agent installer as well as muzzle
 * (both compile and runtime). Extracted out from AgentInstaller to begin separating some of the
 * logic out.
 */
public final class AgentTooling {

  private static final AgentLocationStrategy LOCATION_STRATEGY =
      new AgentLocationStrategy(getBootstrapProxy(), getLocators());

  private static final AgentBuilder.PoolStrategy POOL_STRATEGY =
      new AgentCachingPoolStrategy(LOCATION_STRATEGY);

  private static final ThreadLocal<CurrentTransform> CURRENT_TRANSFORM = new ThreadLocal<>();

  public static AgentLocationStrategy locationStrategy() {
    return LOCATION_STRATEGY;
  }

  public static AgentBuilder.PoolStrategy poolStrategy() {
    return POOL_STRATEGY;
  }

  public static AgentBuilder.Listener transformListener() {
    return new ClassTransformListener();
  }

  private static ClassLoader getBootstrapProxy() {
    Iterator<BootstrapProxyProvider> iterator =
        ServiceLoader.load(BootstrapProxyProvider.class, AgentTooling.class.getClassLoader())
            .iterator();
    if (iterator.hasNext()) {
      BootstrapProxyProvider bootstrapProxyProvider = iterator.next();
      return bootstrapProxyProvider.getBootstrapProxy();
    }

    return null;
  }

  private static List<ClassFileLocator> getLocators() {
    List<ClassFileLocator> locators = new ArrayList<>();
    ServiceLoader.load(ClassFileLocatorProvider.class, AgentTooling.class.getClassLoader())
        .forEach(
            provider -> {
              ClassFileLocator locator = provider.getClassFileLocator();
              if (locator != null) {
                locators.add(locator);
              }
            });

    return locators;
  }

  public static boolean isTransforming(ClassLoader classLoader, String className) {
    CurrentTransform currentTransform = CURRENT_TRANSFORM.get();
    if (currentTransform == null) {
      return false;
    }
    return currentTransform.className.equals(className)
        && currentTransform.classLoader == classLoader;
  }

  private static class ClassTransformListener extends AgentBuilder.Listener.Adapter {
    @Override
    public void onDiscovery(
        String typeName, ClassLoader classLoader, JavaModule module, boolean loaded) {
      CURRENT_TRANSFORM.set(new CurrentTransform(classLoader, typeName));
    }

    @Override
    public void onComplete(
        String typeName, ClassLoader classLoader, JavaModule module, boolean loaded) {
      CURRENT_TRANSFORM.remove();
    }
  }

  private static class CurrentTransform {
    private final ClassLoader classLoader;
    private final String className;

    CurrentTransform(ClassLoader classLoader, String className) {
      this.classLoader = classLoader;
      this.className = className;
    }
  }

  private AgentTooling() {}
}
