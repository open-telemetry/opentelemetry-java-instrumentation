/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap;

public interface IndyProxy {

  // Method name does not fit common naming practices on purpose
  // Also, when modifying this make sure to also update string references.
  @SuppressWarnings("all")
  Object __getIndyProxyDelegate();
}
