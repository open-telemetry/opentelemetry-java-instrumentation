/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.opentelemetry.javaagent.tooling.bytebuddy.matcher;

import java.util.Iterator;

public class ThrowOnFirstElement implements Iterator<Object> {
  @Override
  public boolean hasNext() {
    return i++ < 1;
  }

  @Override
  public Object next() {
    throw new RuntimeException("iteration exception");
  }

  private int i = 0;
}
