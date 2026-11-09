/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.testing.common;

import io.opentelemetry.context.Context;

/**
 * A compatibility helper for test code compiled to pre-Java-8 bytecode, which cannot call static
 * interface methods.
 *
 * @deprecated Use {@link Context#current()} directly in test code targeting Java 8 or later. This
 *     class may be removed in a subsequent minor release.
 */
@Deprecated
public class Java8BytecodeBridge {

  /** Calls {@link Context#current()}. */
  public static Context currentContext() {
    return Context.current();
  }

  private Java8BytecodeBridge() {}
}
