/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.extension;

import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationContextProvider;
import io.opentelemetry.javaagent.extension.spi.AgentExtension;
import io.opentelemetry.javaagent.instrumentation.api.InstrumentationContext;
import java.util.List;
import java.util.Map;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.pool.TypePool;

/**
 * This interface contains methods for accessing or creating internal javaagent components that can
 * be used to implement an {@link AgentExtension}.
 */
public interface AgentExtensionTooling {
  /**
   * Returns an {@link InstrumentationContextProvider} implementation that will generate actual
   * implementations of all {@link InstrumentationContext#get(Class, Class)} calls that have same
   * classes as defined in the {@code contextStore}.
   */
  InstrumentationContextProvider createInstrumentationContextProvider(
      Map<String, String> contextStore);

  /**
   * Returns a {@link AgentBuilder.Transformer} that will inject all helper classes listed in {@code
   * helperClassNames} and all helper resource files listed in {@code helperResources}.
   *
   * @param helperClassNames must contain fully qualified class names accessible from the agent
   *     class loader. Example: {@code io.opentelemetry.javaagent.instrumentation.mylibrary.Helper}.
   * @param helperResources must contain valid resource paths accessible from the agent class
   *     loader. Example: {@code META-INF/services/com.mylib.spi.TracingInterface}.
   */
  AgentBuilder.Transformer createHelperInjector(
      List<String> helperClassNames, List<String> helperResources);

  /**
   * Returns a new {@link TypePool} that allows reading classes contained in the passed {@code
   * classLoader}. The returned pool uses efficient, caching {@link AgentBuilder.PoolStrategy} and
   * {@link AgentBuilder.LocationStrategy} implementations - same ones as the javaagent.
   */
  TypePool createTypePool(ClassLoader classLoader);

  /**
   * Returns the default {@link Advice.ExceptionHandler} for the javaagent. All exceptions thrown by
   * the advice code will be handled by it.
   *
   * @see Advice.OnMethodEnter#suppress() If it's equal to {@code Throwable.class} the registered
   *     exception handler will be called.
   * @see Advice.OnMethodExit#suppress() If it's equal to {@code Throwable.class} the registered
   *     exception handler will be called.
   */
  Advice.ExceptionHandler adviceExceptionHandler();

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
