/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap;

public interface AgentStarter {

  /**
   * When running on oracle jdk8 before 1.8.0_40 loading lambda classes inside agent premain will
   * cause jvm to crash later when lambdas get jit compiled. To circumvent this crash we delay agent
   * initialization to right before main method is called where loading lambda classes work fine.
   *
   * @return true when agent initialization will continue from a callback
   */
  boolean delayStart();

  /** Transfer control to startup logic in agent class loader. */
  void start();

  /**
   * Get extension class loader.
   *
   * @return class loader that is capable of loading configured extensions
   */
  ClassLoader getExtensionClassLoader();
}
