/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.internal.extension;

import io.opentelemetry.javaagent.extension.AgentExtensionTooling;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationContextProvider;
import io.opentelemetry.javaagent.extension.spi.AgentExtension;
import io.opentelemetry.javaagent.tooling.AgentTooling;
import io.opentelemetry.javaagent.tooling.HelperInjector;
import io.opentelemetry.javaagent.tooling.Utils;
import io.opentelemetry.javaagent.tooling.bytebuddy.ExceptionHandlers;
import io.opentelemetry.javaagent.tooling.context.FieldBackedProvider;
import io.opentelemetry.javaagent.tooling.context.NoopContextProvider;
import java.util.List;
import java.util.Map;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.pool.TypePool;

public final class AgentExtensionToolingImpl
    implements AgentExtensionTooling, AgentExtensionTooling.ClassLoaders {

  private final Class<? extends AgentExtension> extensionClass;
  private final String extensionName;

  public AgentExtensionToolingImpl(
      Class<? extends AgentExtension> extensionClass, String extensionName) {
    this.extensionClass = extensionClass;
    this.extensionName = extensionName;
  }

  @Override
  public InstrumentationContextProvider createInstrumentationContextProvider(
      Map<String, String> contextStore) {
    if (!contextStore.isEmpty()) {
      return new FieldBackedProvider(extensionClass, contextStore);
    } else {
      return NoopContextProvider.INSTANCE;
    }
  }

  @Override
  public AgentBuilder.Transformer createHelperInjector(
      List<String> helperClassNames, List<String> helperResources) {
    return new HelperInjector(extensionName, helperClassNames, helperResources);
  }

  @Override
  public TypePool createTypePool(ClassLoader classLoader) {
    return AgentTooling.poolStrategy()
        .typePool(AgentTooling.locationStrategy().classFileLocator(classLoader), classLoader);
  }

  @Override
  public Advice.ExceptionHandler adviceExceptionHandler() {
    return ExceptionHandlers.defaultExceptionHandler();
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
