/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.spi;

/**
 * {@link ComponentInstaller} can be used to install any implementation providers that are used by
 * instrumentations. For instance Java agent uses this to install OpenTelemetry SDK. The
 * instrumentation uses shaded OpenTelemetry API that lives in the bootstrap classloader and the
 * implementation (SDK) is installed via service loader from agent's classloader. This way the
 * application does not have a direct access to the OpenTelemetry SDK classes. The same approach can
 * be done for other APIs used by custom instrumentations.
 *
 * <p>This is a service provider interface that requires implementations to be registered in {@code
 * META-INF/services} folder.
 */
public interface ComponentInstaller {

  /**
   * Runs before instrumentations are installed to ByteBuddy. Execute only a minimal code because
   * any classes loaded before the instrumentations are installed will have to be retransformed,
   * which takes extra time, and more importantly means that fields can't be added to those classes
   * and InstrumentationContext falls back to the less performant Map implementation for those
   * classes.
   */
  default void beforeByteBuddyAgent() {}

  /** Runs after instrumentations are added to ByteBuddy. */
  default void afterByteBuddyAgent() {}
}
