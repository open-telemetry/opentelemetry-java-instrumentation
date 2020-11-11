/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.spi;

/**
 * {@link AgentInstallerExtension} runs at the beginning of {@code AgentInstaller} in agent's
 * classloader. It can be used to load resources available in agent's classloader. For instance
 * instrumentation classes might use an API that is backed by dynamically loaded implementation
 * (e.g. via service loader) available in agent classloader.
 *
 * <p>Before using service loader set the context classloader to agent's classloader e.g. {@code
 * Thread.currentThread().setContextClassLoader(AgentInstaller.class.getClassLoader())}.
 *
 * <p>This is a service provider interface that requires implementations to be registered in
 * `META-INF/services` folder.
 */
public interface AgentInstallerExtension {

  /** Run the provider in agent's classloader. The method must not throw any exception. */
  void run();
}
