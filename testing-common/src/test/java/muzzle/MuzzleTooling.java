/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package muzzle;

import io.opentelemetry.javaagent.extension.AgentExtensionTooling;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationContextProvider;
import io.opentelemetry.javaagent.tooling.AgentTooling;
import io.opentelemetry.javaagent.tooling.Utils;
import java.util.List;
import java.util.Map;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.pool.TypePool;

public final class MuzzleTooling
    implements AgentExtensionTooling, AgentExtensionTooling.ClassLoaders {

  private static final AgentExtensionTooling INSTANCE = new MuzzleTooling();

  public static AgentExtensionTooling instance() {
    return INSTANCE;
  }

  private MuzzleTooling() {}

  @Override
  public InstrumentationContextProvider createInstrumentationContextProvider(
      Map<String, String> contextStore) {
    throw new UnsupportedOperationException("not used by muzzle");
  }

  @Override
  public AgentBuilder.Transformer createHelperInjector(
      List<String> helperClassNames, List<String> helperResources) {
    throw new UnsupportedOperationException("not used by muzzle");
  }

  @Override
  public TypePool createTypePool(ClassLoader classLoader) {
    return AgentTooling.poolStrategy()
        .typePool(AgentTooling.locationStrategy().classFileLocator(classLoader), classLoader);
  }

  @Override
  public Advice.ExceptionHandler adviceExceptionHandler() {
    throw new UnsupportedOperationException("not used by muzzle");
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
    throw new UnsupportedOperationException("not used by muzzle");
  }
}
