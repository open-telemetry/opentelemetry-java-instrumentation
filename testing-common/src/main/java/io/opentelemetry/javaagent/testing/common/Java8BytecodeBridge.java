/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.testing.common;

import io.opentelemetry.context.Context;

/**
 * A helper for scala and kotlin test code, since those tests are compiled to Java 6 bytecode, and
 * so they cannot access methods that rely on new Java 8 bytecode features such as calling a static
 * interface methods.
 */
public final class Java8BytecodeBridge {

  /** Calls {@link Context#current()}. */
  public static Context currentContext() {
    return Context.current();
  }
}
