/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap;

/** Interface added to indy proxies to allow unwrapping the proxy object */
public interface InstrumentationProxy {

  /**
   * Unwraps the proxy delegate instance
   *
   * @return wrapped object instance
   */
  // Method name does not fit common naming practices on purpose
  // Also, when modifying this make sure to also update string references.
  @SuppressWarnings("all")
  Object __getIndyProxyDelegate();
}
