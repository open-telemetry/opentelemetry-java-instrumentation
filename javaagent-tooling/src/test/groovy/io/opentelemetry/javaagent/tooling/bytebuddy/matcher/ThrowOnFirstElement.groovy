/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.bytebuddy.matcher

class ThrowOnFirstElement implements Iterator<Object> {

  int i = 0

  @Override
  boolean hasNext() {
    return i++ < 1
  }

  @Override
  Object next() {
    throw new Exception("iteration exception")
  }
}
