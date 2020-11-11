/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.spi;

/**
 * {@link ComponentInstaller} can be used to install to install any implementation providers that
 * are used by instrumentations. For instance Java agent uses this to install OpenTelemetry SDK. The
 * instrumentation uses shaded OpenTelemetry API that lives in the bootstrap classlaoder and the
 * implementation (SDK) is installed via service loader from agent's classloader. This way the
 * application does not have a direct access to the OpenTelemetry SDK classes. The same approach can
 * be done for other APIs used by custom instrumentations.
 *
 * <p>Before using service loader set the context classloader to agent's classloader e.g. {@code
 * Thread.currentThread().setContextClassLoader(ComponentInstaller.class.getClassLoader())} if the
 * component does not accept classloader when loading implementation via service loader.
 *
 * <p>This is a service provider interface that requires implementations to be registered in {@code
 * META-INF/services} folder.
 */
public interface ComponentInstaller {

  /**
   * Runs before instrumentations are installed to ByteBuddy. Execute only a minimal code because it
   * agent boostrap sequence and instrumentations should be installed as early as possible.
   */
  void beforeByteBuddyAgent();

  /** Runs after instrumentations are added to ByteBuddy. */
  void afterByteBuddyAgent();
}
