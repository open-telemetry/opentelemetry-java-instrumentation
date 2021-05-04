/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.extension;

import io.opentelemetry.javaagent.extension.spi.AgentExtension;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.pool.TypePool;

/**
 * This interface contains methods for accessing or creating internal javaagent components that can
 * be used to implement an {@link AgentExtension}.
 */
public interface AgentExtensionTooling {
  /**
   * Returns a new {@link TypePool} that allows reading classes contained in the passed {@code
   * classLoader}. The returned pool uses efficient, caching {@link AgentBuilder.PoolStrategy} and
   * {@link AgentBuilder.LocationStrategy} implementations - same ones as the javaagent.
   */
  TypePool createTypePool(ClassLoader classLoader);

  /**
   * Returns an object providing easy access to various class loaders used by the javaagent
   * internally.
   */
  ClassLoaders classLoaders();

  interface ClassLoaders {
    /**
     * Returns a non-null {@link ClassLoader} that can be used to load classes/resources from the
     * bootstrap classloader.
     */
    ClassLoader bootstrapProxyClassLoader();

    /** Returns the agent classloader. */
    ClassLoader agentClassLoader();
  }
}
