/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.bytebuddy.matcher;

import java.util.Iterator;

class ThrowOnFirstElement implements Iterator<Object> {

  private int i = 0;

  @Override
  public boolean hasNext() {
    return i++ < 1;
  }

  @Override
  public Object next() {
    throw new RuntimeException("iteration exception");
  }
}