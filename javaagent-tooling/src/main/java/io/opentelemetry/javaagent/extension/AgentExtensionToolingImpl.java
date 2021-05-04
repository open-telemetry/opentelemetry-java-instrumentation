/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.extension;

import io.opentelemetry.javaagent.tooling.AgentTooling;
import io.opentelemetry.javaagent.tooling.Utils;
import net.bytebuddy.pool.TypePool;

public final class AgentExtensionToolingImpl
    implements AgentExtensionTooling, AgentExtensionTooling.ClassLoaders {

  @Override
  public TypePool createTypePool(ClassLoader classLoader) {
    return AgentTooling.poolStrategy()
        .typePool(AgentTooling.locationStrategy().classFileLocator(classLoader), classLoader);
  }

  @Override
  public ClassLoaders classLoaders() {
    return this;
  }

  @Override
  public ClassLoader bootstrapProxyClassLoader() {
    return Utils.getBootstrapProxy();
  }

  @Override
  public ClassLoader agentClassLoader() {
    return Utils.getAgentClassLoader();
  }
}
