/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.test;

import javax.annotation.Nullable;

public interface AnotherTestInterface extends TestInterface {
  void bar();

  @Override
  int hashCode();

  @Override
  boolean equals(@Nullable Object other);

  Object clone();

  @SuppressWarnings("checkstyle:NoFinalizer")
  void finalize();
}
