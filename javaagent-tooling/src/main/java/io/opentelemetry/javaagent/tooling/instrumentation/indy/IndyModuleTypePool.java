/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.instrumentation.indy;

import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.muzzle.AgentTooling;
import net.bytebuddy.pool.TypePool;

public class IndyModuleTypePool {

  private IndyModuleTypePool() {}

  /**
   * Provides a {@link TypePool} which has the same lookup rules for {@link
   * net.bytebuddy.description.type.TypeDescription}s as {@link InstrumentationModuleClassLoader}
   * have for classes.
   *
   * @param instrumentedCl the classloader being instrumented (e.g. for which the {@link
   *     InstrumentationModuleClassLoader} is being created).
   * @param module the {@link InstrumentationModule} performing the instrumentation
   * @return the type pool, must not be cached!
   */
  public static TypePool get(ClassLoader instrumentedCl, InstrumentationModule module) {
    // TODO: this implementation doesn't allow caching the returned pool and its types
    // This could be improved by implementing a custom TypePool instead, which delegates to parent
    // TypePools and mirrors the delegation model of the InstrumentationModuleClassLoader
    InstrumentationModuleClassLoader dummyCl =
        IndyModuleRegistry.createInstrumentationModuleClassloader(module, instrumentedCl);
    return TypePool.Default.of(AgentTooling.locationStrategy().classFileLocator(dummyCl));
  }
}
